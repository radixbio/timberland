package com.radix.timberland.util

import cats.data.EitherT
import cats.effect.{Blocker, ContextShift, IO, Sync, Timer}
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault._
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.syntax._
import io.circe.generic.auto._
import com.radix.utils.tls.ConsulVaultSSLContext.blaze

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.radix.util.sheets.interp.GoogleOAuth
import gsheets4s.model.Credentials
import org.http4s.Uri

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.concurrent.ExecutionContext

object OAuthController {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)
  val config: Config = ConfigFactory.load()

  def creds: IO[Credentials[IO]] = {
    Blocker[IO].use { blocker =>
      val ec: ExecutionContext = blocker.blockingContext
      implicit val interp: GoogleOAuth[IO] =
        new GoogleOAuth(config, blocker)
      val res = for {
        auth <- EitherT[IO, interp.OAuthError, interp.OAuthToken](interp.main)
        creds <- EitherT.liftF[IO, interp.OAuthError, Credentials[IO]](IO.delay(Credentials(auth.token, IO.pure(""))))
      } yield creds
      val T = Timer[IO]

      res.value.flatMap({
        case Left(err)   => T.sleep(1.second) *> IO(scribe.error(s"-----Credential error: ${err}")) *> creds
        case Right(succ) => IO.pure(succ)
      })
    }
  }

  def vaultOauthBootstrap(vaultStatus: VaultSealStatus, vaultBaseUrl: Uri): IO[Unit] = {
    val oauthId = sys.env.get("GOOGLE_OAUTH_ID")
    val oauthSecret = sys.env.get("GOOGLE_OAUTH_SECRET")
    vaultStatus match {
      case VaultAlreadyUnsealed =>
        initializeGoogleOauthPlugin(VaultUtils.findVaultToken(), vaultBaseUrl)
      case VaultUnsealed(_, token) if oauthId.isDefined && oauthSecret.isDefined =>
        initializeGoogleOauthPlugin(token, vaultBaseUrl)
      case VaultUnsealed(_, _) =>
        IO(
          scribe.warn(
            "GOOGLE_OAUTH_ID and/or GOOGLE_OAUTH_SECRET are not set. The Google oauth plugin will not be initialized."
          )
        )
      case VaultSealed =>
        IO(
          scribe.warn(
            "Vault remains sealed. Please check your configuration."
          )
        )
    }
  }

  /**
   * *
   * Initialize Google OAuth plugin if creds are provided via envvars.
   *
   * @param token
   * @param baseUrl
   * @return IO[Unit]
   */
  private def initializeGoogleOauthPlugin(token: String, baseUrl: Uri): IO[Unit] = {
    scribe.trace("Registering Google Plugin...")

    val vaultSession = new VaultSession[IO](authToken = Some(token), baseUrl = baseUrl)
    for {
      _ <- IO.sleep(2.seconds)

      pluginBinaryName = "vault-plugin-secrets-oauthapp"
      pluginBinary = RadPath.persistentDir.toNIO.resolve("vault").resolve(pluginBinaryName)
      pluginHash <- Util.hashFile[IO](pluginBinary)
      registerPluginRequest = RegisterPluginRequest(
        pluginHash,
        "vault-plugin-secrets-oauthapp"
      )
      _ <- vaultSession.registerPlugin(Secret(), "oauth2", registerPluginRequest)

      enableEngineRequest = EnableSecretsEngine("oauth2")
      _ <- vaultSession.enableSecretsEngine("oauth2/google", enableEngineRequest)

      oauthConfigRequest = CreateOauthServerRequest(
        client_id = sys.env.getOrElse("GOOGLE_OAUTH_ID", ""),
        client_secrets = sys.env.get("GOOGLE_OAUTH_SECRET").map(List(_)).getOrElse(Nil),
        provider = "google",
      )
      _ <- vaultSession.createOauthServer("oauth2/google", "google", oauthConfigRequest)
      _ <- IO(scribe.info("OAuth plugin installed"))
    } yield ()
  }
}
