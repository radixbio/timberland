package com.radix.timberland.runtime

import com.radix.timberland.launch.daemonutil

import scala.concurrent.duration._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.util.VaultUtils
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.http4s.Method._
import org.http4s.{Header, Status}
import org.http4s.client.blaze.BlazeClientBuilder
import os.CommandResult

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

object acl {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

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
      _ <- exec(defaultPolicyCreationCmd)

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
      _ <- daemonutil.waitForDNS("nomad.service.consul", 15 seconds)
      _ <- IO.sleep(5 seconds)

      bootstrapCmd = s"$nomad acl bootstrap -address=$addr"
      bootstrapCmdRes <- exec(bootstrapCmd)
      masterToken = parseToken(bootstrapCmdRes)

      consulMasterTokenCreationCmd = s"$consul acl token create -secret=$masterToken -token=$consulToken -policy-id=$masterPolicy"
      _ <- exec(consulMasterTokenCreationCmd)

      _ <- IO.pure(scribe.info(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
    } yield masterToken
  }

  def storeMasterTokenInVault(persistentDir: os.Path, masterToken: String): IO[Unit] = {
    val vaultToken = (new VaultUtils).findVaultToken()
    val vault = persistentDir / "vault" / "vault"
    IO {
      os.proc(vault,
        "kv",
        "put",
        s"-address=http://vault.service.consul:8200",
        "secret/consul-ui-token",
        s"token=$masterToken"
      ).call(stdout = os.Inherit, stderr = os.Inherit, env = Map("VAULT_TOKEN" -> vaultToken))
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
