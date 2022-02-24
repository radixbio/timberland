package com.radix.utils.helm.elemental

import cats.effect.{ContextShift, IO, Timer}
import org.http4s.Uri.uri
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault.CreateSecretRequest

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.syntax._
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import io.circe.Json

sealed trait VaultResult
case object UPSet extends VaultResult
case object UPNotSet extends VaultResult
case class UPRetrieved(username: String, password: String) extends VaultResult
case object UPNotRetrieved extends VaultResult

class ElementalOps(authToken: String) {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def writeUsernamePassword(username: String, password: String): IO[VaultResult] = {
    val upMap = Map("username" -> username, "password" -> password).asJson
    val vaultSession =
      new VaultSession[IO](authToken = Some(authToken), baseUrl = uri("https://vault.service.consul:8200"))
    for {
      secretRequest <- IO(CreateSecretRequest(data = upMap, cas = None))
      vaultResult <- vaultSession.createSecret("elemental-credentials", secretRequest)
      result <- vaultResult match {
        case Right(_) => IO.pure(UPSet)
        case Left(error) => {
          error.printStackTrace()
          IO.pure(UPNotSet)
        }
      }
    } yield result
  }

  def getUsernamePassword: IO[VaultResult] = {
    val vaultSession =
      new VaultSession[IO](authToken = Some(authToken), baseUrl = uri("https://vault.service.consul:8200"))
    for {
      vaultResult <- vaultSession.getSecret[Json]("elemental-credentials")
      result <- vaultResult match {
        case Right(up) => {
          (up.data.hcursor.downField("username").as[String], up.data.hcursor.downField("password").as[String]) match {
            case (Right(u), Right(p)) => IO.pure(UPRetrieved(u, p))
            case (_, _)               => IO.pure(UPNotRetrieved)
          }
        }
        case Left(_) => IO.pure(UPNotRetrieved)
      }
    } yield result
  }
}
