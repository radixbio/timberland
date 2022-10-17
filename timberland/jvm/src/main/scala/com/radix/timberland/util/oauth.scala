package com.radix.timberland.util

import cats.data.EitherT
import cats.effect.{Blocker, ContextShift, IO, Timer}
import org.http4s.Uri.uri
import org.http4s.client.blaze.BlazeClientBuilder
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault._
import com.typesafe.config.{Config, ConfigFactory}
import cats.effect.implicits._
import cats.implicits._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser.decode
import com.radix.timberland.flags.hooks.{awsAuthConfig, AWSAuthConfigFile}
import com.radix.timberland.runtime.AuthTokens
import com.radix.utils.tls.ConsulVaultSSLContext.blaze

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.radix.util.sheets.interp.GoogleOAuth
import gsheets4s.model.Credentials
import org.http4s.Uri

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
        case Left(err)   => T.sleep(1.second) *> IO(scribe.info(s"-----Credential error: ${err}")) *> creds
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
          scribe.info(
            "GOOGLE_OAUTH_ID and/or GOOGLE_OAUTH_SECRET are not set. The Google oauth plugin will not be initialized."
          )
        )
      case VaultSealed =>
        IO(
          scribe.info(
            "Vault remains sealed. Please check your configuration."
          )
        )
    }
  }

  /** *
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

      registerPluginRequest = RegisterPluginRequest(
        "aece93ff2302b7ee5f90eebfbe8fe8296f1ce18f084c09823dbb3d3f0050b107",
        "vault-plugin-secrets-oauthapp"
      )
      _ <- vaultSession.registerPlugin(Secret(), "oauth2", registerPluginRequest)

      enableEngineRequest = EnableSecretsEngine("oauth2")
      _ <- vaultSession.enableSecretsEngine("oauth2/google", enableEngineRequest)

      oauthConfigRequest = CreateSecretRequest(
        data = RegisterProvider("google",
          sys.env.getOrElse("GOOGLE_OAUTH_ID", ""),
          sys.env.getOrElse("GOOGLE_OAUTH_SECRET", "")
        ).asJson,
        cas = None
      )
      _ <- vaultSession.createOauthSecret("oauth2/google/config", oauthConfigRequest)
      _ <- IO(scribe.info("OAuth plugin installed"))
    } yield ()
  }
}
