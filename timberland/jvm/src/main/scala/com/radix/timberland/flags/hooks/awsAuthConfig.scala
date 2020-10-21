package com.radix.timberland.flags.hooks

import cats.effect.IO
import cats.syntax.all._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.RadPath
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

  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] =
    writeAuthConfig(options, addrs)

  private def writeAuthConfig(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] =
    (options.get("aws_access_key_id").flatten, options.get("aws_secret_access_key").flatten) match {
      case (pendingKey @ Some(_), pendingSecret @ Some(_)) =>
        IO {
          val credsExistInVault =
            if (!os.exists(configFile)) false
            else {
              decode[AWSAuthConfigFile](os.read(configFile)).toTry.get.credsExistInVault
            }
          val newConfig = AWSAuthConfigFile(credsExistInVault, pendingKey, pendingSecret)
          os.write.over(configFile, newConfig.asJson.toString)
        }
      case _ => IO.unit
    }

}
