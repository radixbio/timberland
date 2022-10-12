package com.radix.timberland.flags.hooks

import cats.effect.{ContextShift, IO}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.RegisterProvider
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateOauthServerRequest, CreateSecretRequest}
import com.radix.utils.tls.ConsulVaultSSLContext._
import org.http4s.Uri
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global

object oauthConfig extends FlagHook {
  override val possibleOptions: Set[String] = Set("google_oauth_id", "google_oauth_secret")

  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def run(options: Map[String, String], serviceAddrs: ServiceAddrs, tokens: AuthTokens): IO[Unit] = {
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    val vaultSession = new Vault[IO](authToken = tokens.vaultToken, baseUrl = vaultUri)
    val oauthConfigRequest = CreateOauthServerRequest(
      client_id = options("google_oauth_id"),
      client_secrets = List(options("google_oauth_secret")),
      provider = "google",
    )
    vaultSession
      .createOauthServer("oauth2/google", "google", oauthConfigRequest)
      .map {
        _.left.map { err =>
          scribe.error("Error enabling oauth: " + err)
        }
      }
      .map(_ => ())
  }
}
