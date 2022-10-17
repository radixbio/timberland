package com.radix.timberland.flags.hooks

import cats.effect.IO
import cats.syntax.all._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}

import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._

case class AWSAuthConfigFile(
  credsExistInVault: Boolean,
  pendingKey: Option[String] = None,
  pendingSecret: Option[String] = None
)

object awsAuthConfig extends FlagHook {
  val configFile = RadPath.runtime / "terraform" / ".aws-creds"

  override def run(options: Map[String, Option[String]], _addrs: ServiceAddrs): IO[Unit] =
    (options.get("aws_access_key_id").flatten, options.get("aws_secret_access_key").flatten) match {
      case (pendingKey @ Some(_), pendingSecret @ Some(_)) =>
        IO {
          val credsExistInVault =
            os.exists(configFile) && decode[AWSAuthConfigFile](os.read(configFile)).toTry.get.credsExistInVault
          val newConfig = AWSAuthConfigFile(credsExistInVault, pendingKey, pendingSecret)
          os.write.over(configFile, newConfig.asJson.toString)
        }
      case _ => IO.unit
    }

  def writeAWSCredsToVault(implicit tokens: AuthTokens): IO[Unit] =
    for {
      authCfg <- IO(decode[AWSAuthConfigFile](os.read(configFile)).toTry.get)
      _ <- (authCfg.pendingKey, authCfg.pendingSecret) match {
        case (Some(accessKey), Some(secretKey)) =>
          val vault = RadPath.runtime / "timberland" / "vault" / "vault"
          val caPath = RadPath.runtime / "certs" / "ca" / "cert.pem"
          val env = Map("VAULT_TOKEN" -> tokens.vaultToken, "VAULT_CACERT" -> caPath.toString)
          val newConfig = AWSAuthConfigFile(credsExistInVault = true)
          Util.exec(s"$vault write aws/config/root access_key=$accessKey secret_key=$secretKey", env = env) *>
            IO(os.write.over(awsAuthConfig.configFile, newConfig.asJson.toString))
        case _ => IO.unit
      }
    } yield ()

}
