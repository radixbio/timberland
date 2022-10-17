package com.radix.timberland.flags.hooks

import cats.effect.{ContextShift, IO}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.{RegisterProvider, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, VaultErrorResponse}
import com.radix.utils.tls.ConsulVaultSSLContext._
import org.http4s.Uri
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global

object oauthConfig extends FlagHook {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] =
    (options.get("GOOGLE_OAUTH_ID"), options.get("GOOGLE_OAUTH_SECRET")) match {
      case (Some(Some(a: String)), Some(Some(b: String))) =>
        writeConfigToVault(a, b, addrs).map(_ => ())
      case _ => IO.unit
    }

  private def writeConfigToVault(OAUTH_ID: String, OAUTH_SECRET: String, serviceAddrs: ServiceAddrs): IO[String] =
    for {
      vaultToken <- IO(VaultUtils.findVaultToken())
      vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
      vaultSession = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri)
      oauthConfigRequest = CreateSecretRequest(
        data = RegisterProvider("google", OAUTH_ID, OAUTH_SECRET).asJson,
        cas = None
      )
      writeOauthConfig <- vaultSession.createOauthSecret("oauth2/google/config", oauthConfigRequest)
    } yield writeOauthConfig match {
      case Left(VaultErrorResponse(_ @x)) => x.headOption.getOrElse("empty error resp")
      case Right(())                      => "ok json decode"
    }
}
