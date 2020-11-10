package com.radix.timberland.util

import cats.effect.{ContextShift, IO}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult}
import io.circe._
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

trait MessageKind
case class SlackRecipient(webhook: String) extends MessageKind
case class MTeamsRecipient(webhook: String) extends MessageKind
case class EmailRecipient(username: String, domain: String, password: String) extends MessageKind

object MessageRecipients {
  val messaging_secret = "messaging_targets"

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val blazeclient = BlazeClientBuilder[IO](global).resource

  implicit val encodeRecipientEntry: Encoder[MessageKind] = new Encoder[MessageKind] {
    final def apply(msgk: MessageKind): Json = {
      msgk match {
        case SlackRecipient(webhook) =>
          Json.obj(
            ("type", "slack".asJson),
            ("hook", webhook.asJson)
          )
        case MTeamsRecipient(webhook) =>
          Json.obj(
            ("type", "mteams".asJson),
            ("hook", webhook.asJson)
          )
        case EmailRecipient(un, dom, pwd) =>
          Json.obj(
            ("type", "email".asJson),
            ("username", un.asJson),
            ("domain", dom.asJson),
            ("password", pwd.asJson)
          )
      }
    }
  }

  def updateConfig(config: Json, identifier: String, atts: Json): Json = {
    config.deepMerge(Json.obj((identifier, atts)))
  }

  def addRecipient(identifier: String, medium: MessageKind): IO[Unit] = {
    val vaultToken =
      sys.env.getOrElse("VAULT_TOKEN", os.read(os.root / "opt" / "radix" / "timberland" / ".vault-token"))
    if (vaultToken.isEmpty) {
      throw new RuntimeException("Could not find valid vault token")
    }
    val vault = new Vault[IO](Some(vaultToken), Uri.uri("http://vault.service.consul:8200"))
    vault
      .getSecret(messaging_secret)
      .map({
        case Right(KVGetResult(metadata, data)) => data
        case Left(err) =>
          Json.obj() // TODO this assumes error is due to lack of any preexisting recipients; find better way to confirm that key is missing
        //throw new RuntimeException(s"Error accessing messaging configuration from value vault ($err ${err.getMessage})")
      })
      .map(conf => updateConfig(conf, identifier, encodeRecipientEntry(medium)))
      .flatMap(upconf => vault.createSecret(messaging_secret, CreateSecretRequest(None, upconf)))
      .map(_ => Unit)
  }
}
