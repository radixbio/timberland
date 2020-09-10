package com.radix.timberland.runtime

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.{LogTUI, RadPath, Util, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult, LoginResponse}
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class AuthTokens(consulNomadToken: String, vaultToken: String)

object auth {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  def getAuthTokens(
    isRemote: Boolean,
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
    import com.radix.utils.tls.TrustEveryoneSSLContext.insecureBlaze
    implicit val blaze = insecureBlaze
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    scribe.info(s"connecting to Vault with provided u/p at $vaultUri")

    val unauthenticatedVault = new Vault[IO](authToken = None, baseUrl = vaultUri)(IO.ioConcurrentEffect, insecureBlaze)
    for {
      vaultLoginResponse <- unauthenticatedVault.login(username, password)
      vaultToken = vaultLoginResponse match {
        case Left(err) =>
          scribe.error("Error logging into vault with the specified credentials\n" + err)
          sys.exit(1)
        case Right(LoginResponse(token, _, _, _)) => token
      }

      authenticatedVault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)(
        IO.ioConcurrentEffect,
        insecureBlaze
      )
      consulNomadToken <- getConsulTokenFromVault(authenticatedVault)
    } yield AuthTokens(consulNomadToken, vaultToken)

  }

  private def getLocalAuthTokens(): IO[AuthTokens] = {
    val vaultUri = uri"https://127.0.0.1:8200"
    for {
      vaultToken <- IO(new VaultUtils().findVaultToken())
      consulNomadToken <- blaze.use { client =>
        val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
        getConsulTokenFromVault(vault)
      }
    } yield AuthTokens(consulNomadToken, vaultToken)
  }

  private def getConsulTokenFromVault(vault: Vault[IO]): IO[String] =
    vault.getSecret("consul-ui-token").map {
      case Right(KVGetResult(_, data)) =>
        data.hcursor.get[String]("token").toOption.getOrElse {
          scribe.error("Error parsing consul/nomad token from vault secret")
          sys.exit(1)
        }
      case Left(err) =>
        scribe.error("Error getting consul/nomad token from vault\n" + err)
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
  def setupDefaultConsulToken(persistentDir: os.Path, consulToken: String): IO[Unit] = {
    val consulDir = persistentDir / "consul"
    val defaultPolicy = consulDir / "default-policy.hcl"
    val consul = consulDir / "consul"
    val defaultPolicyCmd = s"$consul acl policy create -token=$consulToken -name=default-policy -rules=@$defaultPolicy"
    val getDefaultPolicyCmd = s"$consul acl policy read -token=$consulToken -name=default-policy"
    val defaultTokenCmd =
      s"$consul acl token create -token=$consulToken -description='Default-allow-DNS.' -policy-name=default-policy"
    for {
      _ <- Util.waitForConsul(consulToken, 60.seconds, address = "http://127.0.0.1:8500")
      defaultPolicyCmdRes <- Util.exec(defaultPolicyCmd)
      checkDefaultPolicyRes <- if (defaultPolicyCmdRes.exitCode == 0) IO.pure(true)
      else if ((defaultPolicyCmdRes.exitCode != 0) && (defaultPolicyCmdRes.stderr
                 .contains("already exists"))) {
        for {
          checkDefaultPolicyRes <- Util.exec(getDefaultPolicyCmd)
          policyAlreadyThere = checkDefaultPolicyRes.stdout.contains(os.read(defaultPolicy))
        } yield policyAlreadyThere
      } else IO.pure(false) // unknown error

      _ <- if (checkDefaultPolicyRes) IO(scribe.info("Policy already exists!"))
      else {
        IO(scribe.error("Unknown error setting up default Consul Policy")); IO(sys.exit(1))
      }
      defaultTokenCmdRes <- Util.exec(defaultTokenCmd)

      dnsRequestToken = parseToken(defaultTokenCmdRes)

      setDefaultTokenCmd = s"$consul acl set-agent-token -token=$consulToken default ${dnsRequestToken.getOrElse("")}"
      _ <- if (defaultTokenCmdRes.exitCode == 0) Util.exec(setDefaultTokenCmd)
      else IO(scribe.error(s"$defaultTokenCmd exited with ${defaultTokenCmdRes.exitCode}"))
    } yield ()
  }

  /**
   *
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken The token used to access consul
   * @return The generated master token (works with both consul and nomad)
   */
  def setupNomadMasterToken(persistentDir: os.Path, consulToken: String): IO[String] = {
    val nomad = persistentDir / "nomad" / "nomad"
    val consul = persistentDir / "consul" / "consul"
    val certDir = persistentDir / os.up / "certs"
    val tlsCa = certDir / "ca" / "cert.pem"
    val tlsCert = certDir / "nomad" / "cli-cert.pem"
    val tlsKey = certDir / "nomad" / "cli-key.pem"
    val addr = "https://nomad.service.consul:4646"
    val masterPolicy = "00000000-0000-0000-0000-000000000001"

    for {
      nomadResolves <- Util.waitForDNS("nomad.service.consul", 60.seconds)
      nomadUp <- Util.waitForPortUp(4646, 60.seconds)
      _ <- Util.waitForNomad(30.seconds)
      _ <- IO.pure(scribe.info(s"nomad resolves: $nomadResolves, nomad listening on 4646: $nomadUp"))

      bootstrapCmd = s"$nomad acl bootstrap -address=$addr -ca-cert=$tlsCa -client-cert=$tlsCert -client-key=$tlsKey"
      bootstrapCmdRes <- Util.exec(bootstrapCmd)
      masterTokenOpt = parseToken(bootstrapCmdRes)
      masterToken <- masterTokenOpt.map(IO.pure).getOrElse(getMasterToken)

      masterTokenCmd = s"$consul acl token create -secret=$masterToken -token=$consulToken -policy-id=$masterPolicy"
      _ <- if (bootstrapCmdRes.exitCode == 0) Util.exec(masterTokenCmd)
      else IO(scribe.error("Failed to parse token from ACL Bootstrap call!"))

      _ <- IO.pure(scribe.info(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
      _ <- IO.pure(LogTUI.printAfter(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
    } yield masterToken
  }

  def getMasterToken: IO[String] = IO {
    os.read(RadPath.runtime / "timberland" / ".acl-token")
  }

  def storeIntermediateToken(token: String) = IO {
    val intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
    if (os.exists(intermediateAclTokenFile)) os.write.over(intermediateAclTokenFile, token, os.PermSet(400))
    else
      os.write(intermediateAclTokenFile, token, os.PermSet(400))
    ()
  }

  def storeMasterToken(masterToken: String): IO[Unit] = {
    val vaultToken = (new VaultUtils).findVaultToken()
    val vaultUri = uri"https://127.0.0.1:8200"
    val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
    val payload = CreateSecretRequest(data = Map("token" -> masterToken).asJson, cas = None)
    vault.createSecret("consul-ui-token", payload).map {
      case Left(err) =>
        scribe.error(s"Error saving consul/nomad token to vault! Failed with $err.")
      case Right(_) => ()
    } *> IO(os.write.over(RadPath.runtime / "timberland" / ".acl-token", masterToken, os.PermSet(400)))
  }

  private def parseToken(cmdResult: Util.ProcOut): Option[String] = {
    val lines = cmdResult.stdout.split('\n')
    val secretLine = lines.find(line => line.startsWith("Secret"))
    secretLine.map(_.split("\\s+").last).orElse {
      scribe.warn("Invalid response from auth bootstrap command:")
      scribe.warn(cmdResult.stdout)
      None
    }
  }
}
