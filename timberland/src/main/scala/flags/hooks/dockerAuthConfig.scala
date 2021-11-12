package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.{LogTUI, Util}

import io.circe._
import io.circe.parser._
import io.circe.optics.JsonPath._

object dockerAuthConfig extends FlagHook {
  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] = {
    for {
      _ <- IO(LogTUI.writeLog("Reg auth handler invoked!"))
      success <- containerRegistryLogin(
        options.getOrElse("ID", None),
        options.getOrElse("TOKEN", None),
        options.getOrElse("URL", Some("registry.gitlab.com")).get
      )
      _ <- if (success != 0) IO(scribe.warn("Container registry login was not successful!")) else IO()
    } yield ()
  }

  private def existingRegistryCredentials(): Option[String] = {
    val existingConfigPath = os.home / ".docker" / "config.json"
    val existingJson =
      if (os.exists(existingConfigPath)) parse(os.read(existingConfigPath)).getOrElse(Json.Null) else Json.Null
    root.auths.`registry.gitlab.com`.auth.string.getOption(existingJson)
  }

  private def containerRegistryLogin(regUser: Option[String], regToken: Option[String], regAddress: String): IO[Int] = {
    val user = if (regUser.nonEmpty) regUser else sys.env.get("CONTAINER_REG_USER")
    val token = if (regToken.nonEmpty) regToken else sys.env.get("CONTAINER_REG_TOKEN")
    (user, token, existingRegistryCredentials) match {
      case (_, _, Some(_)) => IO { scribe.info("Already logged into Docker registry."); 0 }
      case (None, _, _)    => IO { scribe.warn("No user/id provided for container registry, not logging in"); -1 }
      case (_, None, _)    => IO { scribe.warn("No password/token provided for container registry, not logging in"); -1 }
      case (Some(user), Some(token), None) =>
        for {
          procOut <- Util.exec(s"docker login $regAddress -u $user -p $token")
        } yield procOut.exitCode
    }
  }
}
