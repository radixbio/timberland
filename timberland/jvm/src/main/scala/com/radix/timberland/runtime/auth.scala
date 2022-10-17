package com.radix.timberland.runtime

import com.radix.timberland.launch.daemonutil
import com.radix.timberland.util.LogTUI

import scala.concurrent.duration._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.VaultUtils
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult, LoginResponse}
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.http4s.Method._
import org.http4s.{Header, Status, Uri}
import org.http4s.client.blaze.BlazeClientBuilder
import io.circe.syntax._
import os.CommandResult

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

case class AuthTokens(consulNomadToken: String, vaultToken: String)

object auth {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  def getAuthTokens(isRemote: Boolean,
                    serviceAddrs: ServiceAddrs,
                    usernameOption: Option[String],
                    passwordOption: Option[String]
                   ): IO[AuthTokens] = {
    if (isRemote) {
      for {
        username <- IO(usernameOption.getOrElse(System.console.readLine("Vault username>")))
        password <- IO(passwordOption.getOrElse(System.console.readPassword("Vault password>").mkString))
        tokens <- getRemoteAuthTokens(serviceAddrs, username, password)
      } yield tokens
    } else getLocalAuthTokens()
  }

  private def getRemoteAuthTokens(serviceAddrs: ServiceAddrs, username: String, password: String): IO[AuthTokens] = {
    val blaze = BlazeClientBuilder[IO](global).resource
    val vaultUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8200").toOption.get
    blaze.use { client =>
      val unauthenticatedVault = new Vault[IO](authToken = None, baseUrl = vaultUri, blazeClient = client)
      for {
        vaultLoginResponse <- unauthenticatedVault.login(username, password)
        vaultToken = vaultLoginResponse match {
          case Left(err) =>
            Console.err.println("Error logging into vault with the specified credentials\n" + err)
            sys.exit(1)
          case Right(LoginResponse(token, _, _, _)) => token
        }

        authenticatedVault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri, blazeClient = client)
        consulNomadToken <- getConsulTokenFromVault(authenticatedVault)
      } yield AuthTokens(consulNomadToken, vaultToken)
    }
  }

  private def getLocalAuthTokens(): IO[AuthTokens] = {
    val blaze = BlazeClientBuilder[IO](global).resource
    val vaultUri = uri"http://127.0.0.1:8200"
    for {
      vaultToken <- IO(new VaultUtils().findVaultToken())
      consulNomadToken <- blaze.use { client =>
        val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri, blazeClient = client)
        getConsulTokenFromVault(vault)
      }
    } yield AuthTokens(consulNomadToken, vaultToken)
  }

  private def getConsulTokenFromVault(vault: Vault[IO]): IO[String] =
    vault.getSecret("consul-ui-token").map {
      case Right(KVGetResult(_, data)) => data.hcursor.get[String]("token").toOption.getOrElse {
        Console.err.println("Error parsing consul/nomad token from vault secret")
        sys.exit(1)
      }
      case Left(err) =>
        Console.err.println("Error getting consul/nomad token from vault\n" + err)
        sys.exit(1)
    }

  /**
   *
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken This token is set as the superUser token for consul
   * @return Nothing
   */
  def writeTokenConfigs(persistentDir: os.Path, consulToken: String): IO[Unit] = {
    val vaultConfigLoc = persistentDir / "vault" / "vault_config.conf"
    val vaultConfigRegex = """token = ".+""""
    val vaultConfigRegexReplacement = s"""token = "$consulToken""""
    val nomadConfigLoc = persistentDir / "nomad" / "config" / "auth.hcl"
    val nomadConfig = s"""consul { token = "$consulToken" }""".stripMargin
    val consulConfigLoc = persistentDir / "consul" / "config" / "auth.hcl"
    val consulConfig =
      s"""acl {
         |  tokens {
         |    master = "$consulToken"
         |    agent = "$consulToken"
         |  }
         |}
         |""".stripMargin

    IO {
      val vaultConfig = os.read(vaultConfigLoc).replaceAll(vaultConfigRegex, vaultConfigRegexReplacement)
      os.write.over(vaultConfigLoc, vaultConfig)
      os.write.over(nomadConfigLoc, nomadConfig)
      os.write.over(consulConfigLoc, consulConfig)
    }
  }

  /**
   *
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken The token used to access consul
   * @return Nothing
   */
  def setupDefaultConsulToken(persistentDir: os.Path, consulToken: String) = {
    val consulDir = persistentDir / "consul"
    val consul = consulDir / "consul"

    for {
      _ <- waitForConsul(consulToken)

      defaultPolicyCreationCmd = s"$consul acl policy create -token=$consulToken -name=dns-requests -rules=@${consulDir}/default-policy.hcl"
      response <- exec(defaultPolicyCreationCmd)
      _ <- if (response.exitCode != 0) {
        Console.err.println("Partial bootstrap detected! Please reinstall timberland before continuing")
        sys.exit(1)
      } else IO.unit

      defaultTokenCreationCmd = s"$consul acl token create -token=$consulToken -description=DNS -policy-name=dns-requests"
      defaultTokenCreationCmdRes <- exec(defaultTokenCreationCmd)
      dnsRequestToken = parseToken(defaultTokenCreationCmdRes)

      setDefaultTokenCmd = s"$consul acl set-agent-token -token=$consulToken default $dnsRequestToken"
      _ <- exec(setDefaultTokenCmd)
    } yield ()
  }

  /**
   *
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken The token used to access consul
   * @return The generated master token (works with both consul and nomad)
   */
  def setupNomadMasterToken(persistentDir: os.Path, consulToken: String) = {
    val nomad = persistentDir / "nomad" / "nomad"
    val consul = persistentDir / "consul" / "consul"
    val addr = "http://nomad.service.consul:4646"
    val masterPolicy = "00000000-0000-0000-0000-000000000001"

    for {
      nomadResolves <- daemonutil.waitForDNS("nomad.service.consul", 60.seconds)
      nomadUp <- daemonutil.waitForPortUp(4646, 60.seconds)
      _ <- IO.sleep(10.seconds)
      _ <- IO.pure(scribe.info(s"nomad resolves: ${nomadResolves}, nomad listening on 4646: ${nomadUp}"))

      bootstrapCmd = s"$nomad acl bootstrap -address=$addr"
      bootstrapCmdRes <- exec(bootstrapCmd)
      masterToken = parseToken(bootstrapCmdRes)

      consulMasterTokenCreationCmd = s"$consul acl token create -secret=$masterToken -token=$consulToken -policy-id=$masterPolicy"
      _ <- exec(consulMasterTokenCreationCmd)

      _ <- IO.pure(scribe.info(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
      _ <- IO.pure(LogTUI.printAfter(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
    } yield masterToken
  }

  def storeMasterTokenInVault(masterToken: String): IO[Unit] = {
    val vaultToken = (new VaultUtils).findVaultToken()
    val vaultUri = uri"http://127.0.0.1:8200"
    val blaze = BlazeClientBuilder[IO](global).resource
    blaze.use { client =>
      val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri, blazeClient = client)
      val payload = CreateSecretRequest(data = Map("token" -> masterToken).asJson, cas = None)
      vault.createSecret("consul-ui-token", payload).map {
        case Left(err) =>
          Console.err.println("Error saving consul/nomad token to vault\n" + err)
          sys.exit(1)
        case Right(_) => ()
      }
    }
  }

  def exec(command: String) = IO {
    os.proc(command.split(' ')).call(stderr = os.Inherit, cwd = os.root, check = false)
  }

  def parseToken(cmdResult: CommandResult) = {
    val lines = cmdResult.out.text().split('\n')
    val secretLine = lines.find(line => line.startsWith("Secret"))
    secretLine match {
      case Some(line) => line.split("\\s+").last
      case None => {
        scribe.error("Could not bootstrap consul. Exiting...")
        sys.exit(1)
      }
    }
  }

  def waitForConsul(consulToken: String): IO[Unit] = {
    val request = GET(uri"http://127.0.0.1:8500/v1/agent/self", Header("X-Consul-Token", consulToken))
    def queryConsul: IO[Unit] = {
      BlazeClientBuilder[IO](global).resource
        .use { client => client.status(request).attempt }
        .flatMap {
          case Right(Status(200)) => IO.unit
          case thing@_ => IO.sleep(1 second) *> queryConsul
        }
    }

    daemonutil.timeout(queryConsul, 30 minutes)
  }
}
