package com.radix.timberland.flags.hooks

import cats.effect.IO
import cats.syntax.all._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._

import scala.util.Random

case class AWSAuthConfigFile(
  credsExistInVault: Boolean,
  pendingKey: Option[String] = None,
  pendingSecret: Option[String] = None
)

object awsAuthConfig extends FlagHook {
  val configFile = RadPath.runtime / "timberland" / ".aws-creds"

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

  def writeMinioAndAWSCredsToVault(implicit tokens: AuthTokens): IO[Unit] = {
    val vault = RadPath.runtime / "timberland" / "vault" / "vault"
    val caPath = RadPath.runtime / "certs" / "ca" / "cert.pem"
    val env = Map("VAULT_TOKEN" -> tokens.vaultToken, "VAULT_CACERT" -> caPath.toString)
    val minioAccessKey = Random.alphanumeric.take(20).toList.mkString
    val minioSecretKey = Random.alphanumeric.take(20).toList.mkString
    val minioKeysCmd = s"$vault kv put -cas=0 secret/minio-creds access_key=$minioAccessKey secret_key=$minioSecretKey"
    for {
      authCfg <- IO(decode[AWSAuthConfigFile](os.read(configFile)).toTry.get)
      _ <- Util.exec(minioKeysCmd, env = env)
      _ <- (authCfg.pendingKey, authCfg.pendingSecret) match {
        case (Some(awsAccessKey), Some(awsSecretKey)) =>
          val newConfig = AWSAuthConfigFile(credsExistInVault = true)
          Util.exec(s"$vault write aws/config/root access_key=$awsAccessKey secret_key=$awsSecretKey", env = env) *>
            IO(os.write.over(awsAuthConfig.configFile, newConfig.asJson.toString))
        case _ => IO.unit
      }
    } yield ()
  }
}
