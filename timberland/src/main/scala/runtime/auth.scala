package com.radix.timberland.runtime

import cats.effect.{ContextShift, IO, Timer}
import com.radix.timberland.radixdefs.{ACLTokens, ServiceAddrs}
import com.radix.timberland.util.{RadPath, Util, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult, LoginResponse}
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import io.circe.Json
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class AuthTokens(consulNomadToken: String, actorToken: String, vaultToken: String)

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

  def getGossipKey(vaultToken: String, vaultAddress: String): IO[String] = {
    val vaultUri = Uri.fromString(s"https://$vaultAddress:8200").toOption.get
    val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
    val gossipKeyOption = vault.getSecret[Json](s"gossip-key").map {
      case Right(KVGetResult(_, data)) =>
        data.hcursor.get[String]("key").toOption
      case Left(err) =>
        scribe.warn(s"Error getting gossip key from vault\n" + err)
        scribe.warn(s"Error message: ${err.getMessage}")
        None
    }
    gossipKeyOption.flatMap {
      case Some(key) => IO.pure(key)
      case None =>
        val consul = RadPath.persistentDir / "consul" / "consul"
        val keygenCmd = s"$consul keygen"
        for {
          keygenResp <- Util.exec(keygenCmd)
          key = keygenResp.stdout.stripSuffix("\n")
          vaultPayload = CreateSecretRequest(data = Map("key" -> key).asJson, cas = None)
          vaultResp <- vault.createSecret("gossip-key", vaultPayload)
          _ = vaultResp match {
            case Left(err) =>
              scribe.warn(s"Warning, gossip key could not be saved to vault due to:\n" + err)
            case _ => ()
          }
        } yield key
    }
  }

  private def getRemoteAuthTokens(serviceAddrs: ServiceAddrs, username: String, password: String): IO[AuthTokens] = {
    import com.radix.utils.tls.TrustEveryoneSSLContext.insecureBlaze
    implicit val blaze = insecureBlaze
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    scribe.debug(s"connecting to Vault with provided u/p at $vaultUri")

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
      consulNomadToken <- getTokenFromVault(authenticatedVault, "consul-ui-token")
      actorToken <- getTokenFromVault(authenticatedVault, "actor-token")
    } yield AuthTokens(consulNomadToken = consulNomadToken, actorToken = actorToken, vaultToken)

  }

  private def getLocalAuthTokens(): IO[AuthTokens] = {
    val vaultUri = uri"https://127.0.0.1:8200"
    for {
      vaultToken <- IO(VaultUtils.findVaultToken())
      consulNomadToken <- blaze.use { client =>
        val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
        getTokenFromVault(vault, "consul-ui-token")
      }
      actorToken <- blaze.use { client =>
        val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
        getTokenFromVault(vault, "actor-token")
      }
    } yield AuthTokens(consulNomadToken = consulNomadToken, actorToken = actorToken, vaultToken = vaultToken)
  }

  private def getTokenFromVault(vault: Vault[IO], name: String): IO[String] =
    vault.getSecret[Json](s"tokens/$name").map {
      case Right(KVGetResult(_, data)) =>
        data.hcursor.get[String]("token").toOption.getOrElse {
          scribe.error(s"Error parsing $name token from vault secret")
          sys.exit(1)
        }
      case Left(err) =>
        scribe.error(s"Error getting $name token from vault\n" + err)
        scribe.error(s"Error message: ${err.getMessage}")
        sys.exit(1)
    }

  /**
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken This token is set as the superUser token for consul
   * @return Nothing
   */
  def writeVaultTokenConfigs(persistentDir: os.Path, consulToken: String): IO[Unit] = {
    val vaultConfigLoc = persistentDir / "vault" / "vault_config.conf"
    val vaultConfigRegex = """token = ".+""""
    val vaultConfigRegexReplacement = s"""token = "$consulToken""""

    IO {
      val vaultConfig = os.read(vaultConfigLoc).replaceAll(vaultConfigRegex, vaultConfigRegexReplacement)
      os.write.over(vaultConfigLoc, vaultConfig)
    }
  }

  def writeConsulNomadTokenConfigs(persistentDir: os.Path, consulToken: String, vaultToken: String): IO[Unit] = {
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
         |connect {
         |  ca_config {
         |    token = "$vaultToken"
         |  }
         |}
         |""".stripMargin
    IO {
      os.write.over(nomadConfigLoc, nomadConfig)
      os.write.over(consulConfigLoc, consulConfig)
    }
  }

  /**
   * @param persistentDir Usually /opt/radix/timberland/
   * @param consulToken The token used to access consul
   * @return Nothing
   */
  def setupConsulTokens(persistentDir: os.Path, consulToken: String): IO[String] = {
    val consulDir = persistentDir / "consul"
    val consul = consulDir / "consul"

    val defaultTokenCmd =
      s"$consul acl token create -token=$consulToken -description='Default-allow-DNS.' -policy-name=default-policy"

    for {
      defaultTokenRes <- setupConsulToken(persistentDir, consulToken, "default-policy", "Default-allow-DNS")
      actorTokenRes <- setupConsulToken(persistentDir, consulToken, "actor-policy", "akka-actors")
      setDefaultTokenCmd = s"$consul acl set-agent-token -token=$consulToken default ${defaultTokenRes.token}"
      _ <-
        if (defaultTokenRes.cmdRes.exitCode == 0) Util.exec(setDefaultTokenCmd)
        else IO(scribe.error(s"$defaultTokenCmd exited with ${defaultTokenRes.cmdRes.exitCode}"))
    } yield actorTokenRes.token
  }

  private case class TokenResult(cmdRes: Util.ProcOut, token: String)
  private def setupConsulToken(
    persistentDir: os.Path,
    consulToken: String,
    policyName: String,
    tokenDescription: String
  ): IO[TokenResult] = {
    val consulDir = persistentDir / "consul"
    val consul = consulDir / "consul"
    val policyFile = consulDir / s"$policyName.hcl"
    val policyCmd = s"$consul acl policy create -token=$consulToken -name=$policyName -rules=@$policyFile"
    val getPolicyCmd = s"$consul acl policy read -token=$consulToken -name=$policyName"
    val tokenCmd =
      s"$consul acl token create -token=$consulToken -description='$tokenDescription' -policy-name=$policyName"

    for {
      _ <- Util.waitForConsul(consulToken, 60.seconds, address = "http://127.0.0.1:8500")
      _ <- IO.sleep(30.seconds)
      policyCmdRes <- Util.exec(policyCmd)
      checkPolicyCmdRes <-
        if (policyCmdRes.exitCode == 0) IO.pure(true)
        else if (policyCmdRes.exitCode != 0 && policyCmdRes.stderr.contains("already exists")) {
          for {
            checkPolicyRes <- Util.exec(getPolicyCmd)
            policyAlreadyThere = checkPolicyRes.stdout.contains(os.read(policyFile))
          } yield policyAlreadyThere
        } else IO.pure(false) //unknown error

      _ <-
        if (checkPolicyCmdRes) IO(scribe.warn(s"$policyName policy already exists."))
        else {
          IO(scribe.error(s"Unknown error setting up $policyName Consul policy."))
          IO(sys.exit(1))
        }

      tokenCmdRes <- Util.exec(tokenCmd)
      token = parseToken(tokenCmdRes).getOrElse("")
    } yield TokenResult(tokenCmdRes, token)
  }

  /**
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
      nomadResolves <- Util.waitForServiceDNS("nomad", 60.seconds)
      nomadUp <- Util.waitForPortUp(4646, 60.seconds)
      _ <- Util.waitForNomad(30.seconds)
      _ <- IO.pure(scribe.info(s"nomad resolves: $nomadResolves, nomad listening on 4646: $nomadUp"))

      bootstrapCmd = s"$nomad acl bootstrap -address=$addr -ca-cert=$tlsCa -client-cert=$tlsCert -client-key=$tlsKey"
      bootstrapCmdRes <- Util.exec(bootstrapCmd)
      masterTokenOpt = parseToken(bootstrapCmdRes)
      masterToken <- masterTokenOpt.map(IO.pure).getOrElse(getMasterToken)

      masterTokenCmd = s"$consul acl token create -secret=$masterToken -token=$consulToken -policy-id=$masterPolicy"
      _ <-
        if (bootstrapCmdRes.exitCode == 0) Util.exec(masterTokenCmd)
        else IO(scribe.error("Failed to parse token from ACL Bootstrap call!"))

      _ <- IO.pure(scribe.debug(s"ADMIN TOKEN FOR CONSUL/NOMAD: $masterToken"))
    } yield masterToken
  }

  def getMasterToken: IO[String] = IO {
    os.read(RadPath.runtime / "timberland" / ".acl-token")
  }
  import java.io.File

  def storeIntermediateToken(token: String) = IO {
    val intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
    if (os.exists(intermediateAclTokenFile)) {
      // permset doesn't work on windows, so set permissions manually
      // ...maybe a race here?

      val intermediateFile = new File(intermediateAclTokenFile.toString())
      intermediateFile.setWritable(true, true)
      os.write.over(intermediateAclTokenFile, token)
      intermediateFile.setReadable(false, true)
      intermediateFile.setWritable(false, true)
    } else {
      os.write(intermediateAclTokenFile, token)
      val intermediateFile = new File(intermediateAclTokenFile.toString())
      intermediateFile.setReadable(false, true)
      intermediateFile.setWritable(false, true)
    }

    ()
  }

  /**
   * - puts the master consul/nomad token in timberland/.acl-token
   * - stores the master token as "consul-ui-token" in vault
   * - stores another token with less permissions in vault as "actor-token"
   * @param tokens Tokens to store
   * @return
   */
  def storeTokensInVault(tokens: ACLTokens): IO[Unit] = {
    val vaultToken = VaultUtils.findVaultToken()
    val vaultUri = uri"https://127.0.0.1:8200"
    val vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
    List((tokens.masterToken, "consul-ui-token"), (tokens.actorToken, "actor-token"))
      .map {
        case (token, name) => {
          val payload = CreateSecretRequest(data = Map("token" -> token).asJson, cas = None)
          vault.createSecret(s"tokens/${name}", payload).map {
            case Left(err) =>
              scribe.warn(s"Warning, ${name} could not be saved to vault due to:\n" + err)
              scribe.warn(s"This is most likely because the token already exists")
            case Right(_) => ()
          }
        }
      }
      .reduce(_ *> _) *> IO(
      os.write.over(RadPath.runtime / "timberland" / ".acl-token", tokens.masterToken, os.PermSet(400))
    )
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
