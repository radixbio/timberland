package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.{LogTUI, Util}

object dockerAuthConfig extends FlagHook {
  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] = {
    for {
      _ <- IO(LogTUI.writeLog("Reg auth handler invoked!"))
      success <- containerRegistryLogin(
        options.getOrElse("ID", None),
        options.getOrElse("TOKEN", None),
        options.getOrElse("URL", Some("registry.gitlab.com")).get
      )
      _ <- if (success != 0) IO(scribe.warn("Container registry login was not successful!")) else IO(Unit)
    } yield ()
  }

  private def containerRegistryLogin(regUser: Option[String], regToken: Option[String], regAddress: String): IO[Int] = {
    val user = if (regUser.nonEmpty) regUser else sys.env.get("CONTAINER_REG_USER")
    val token = if (regToken.nonEmpty) regToken else sys.env.get("CONTAINER_REG_TOKEN")
    (user, token) match {
      case (None, _) => scribe.warn("No user/id provided for container registry, not logging in"); IO(-1)
      case (_, None) => scribe.warn("No password/token provided for container registry, not logging in"); IO(-1)
      case (Some(user), Some(token)) =>
        for {
          procOut <- Util.exec(s"docker login $regAddress -u $user -p $token")
        } yield procOut.exitCode
    }
  }
}
