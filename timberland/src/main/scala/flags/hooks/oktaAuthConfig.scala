package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._

case class OktaAuthConfigFile(
  pendingBaseUrl: Option[String] = None,
  pendingOrgName: Option[String] = None,
  pendingApiToken: Option[String] = None
)

object oktaAuthConfig extends FlagHook {
  val configFile = RadPath.runtime / "terraform" / ".okta-creds"

  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] = {
    (options.get("base_url").flatten, options.get("org_name").flatten, options.get("api_token").flatten) match {
      case (baseUrl: Some[String], orgName: Some[String], apiToken: Some[String]) =>
        val newConfig = OktaAuthConfigFile(baseUrl, orgName, apiToken)
        IO(os.write.over(configFile, newConfig.asJson.toString))
      case _ => IO.unit
    }
  }

  def writeOktaCredsToVault(implicit tokens: AuthTokens): IO[Unit] = {
    val vault = RadPath.runtime / "timberland" / "vault" / "vault"
    val caPath = RadPath.runtime / "certs" / "ca" / "cert.pem"
    val env = Map("VAULT_TOKEN" -> tokens.vaultToken, "VAULT_CACERT" -> caPath.toString)
    val oktaConfig = IO {
      if (os.exists(configFile)) {
        decode[OktaAuthConfigFile](os.read(configFile)).toTry.get
      } else {
        OktaAuthConfigFile()
      }
    }
    oktaConfig.flatMap {
      case OktaAuthConfigFile(Some(baseUrl), Some(orgName), Some(apiToken)) =>
        for {
          _ <- Util.exec(s"$vault auth enable okta", env = env)
          configCmd = s"$vault write auth/okta/config base_url=$baseUrl org_name=$orgName api_token=$apiToken"
          _ <- Util.exec(configCmd, env = env)
          emptyConfig = OktaAuthConfigFile()
          _ <- IO(os.write.over(oktaAuthConfig.configFile, emptyConfig.asJson.toString))
        } yield ()
      case _ => IO.unit
    }
  }
}
