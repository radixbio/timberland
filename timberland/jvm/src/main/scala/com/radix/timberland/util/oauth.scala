package com.radix.timberland

import cats.data.EitherT
import cats.effect.{Blocker, ContextShift, IO, Timer}
import org.http4s.Uri.uri
import org.http4s.client.blaze.BlazeClientBuilder
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault._
import com.typesafe.config.{Config, ConfigFactory}
import cats.effect.implicits._
import cats.implicits._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import com.radix.util.sheets.interp.GoogleOAuth
import gsheets4s.model.Credentials

import scala.concurrent.ExecutionContext

sealed trait OAuthStatus
case object OAuthUrlRetrieved extends OAuthStatus
case object OAuthError extends OAuthStatus

object OAuthController {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)
  val config: Config = ConfigFactory.load()

  def creds: IO[Credentials[IO]] = {
    Blocker[IO].use { blocker =>
      val ec: ExecutionContext = blocker.blockingContext
      BlazeClientBuilder[IO](ec).resource.use { blazeClient =>
        implicit val interp: GoogleOAuth[IO] =
          new GoogleOAuth[IO](config, blazeClient, blocker)
        val res = for {
          auth <- EitherT[IO, interp.OAuthError, interp.OAuthToken](interp.main)
          creds <- EitherT.liftF[IO, interp.OAuthError, Credentials[IO]](
            IO.delay(Credentials(auth.token, IO.pure(""))))
        } yield creds
        val T = Timer[IO]

        res.value.flatMap({
          case Left(err) => T.sleep(1.second) *> IO(scribe.info(s"-----Credential error: ${err}")) *> creds
          case Right(succ) => IO.pure(succ)
        })
      }
    }
  }
}
