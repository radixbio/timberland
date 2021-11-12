package com.radix.utils.helm.vault

import java.time.OffsetDateTime

import io.circe.{ACursor, Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.generic.semiauto._

sealed trait VaultError extends Throwable
final case class VaultConnectionError() extends VaultError
final case class VaultErrorMalformedResponse() extends VaultError
final case class VaultErrorResponse(errors: Vector[String]) extends VaultError
object VaultErrorResponse {
  implicit val vaultErrorResponseDecoder: Decoder[VaultErrorResponse] =
    deriveDecoder[VaultErrorResponse]
}

final case class InitRequest(
  secret_shares: Int,
  secret_threshold: Int,
  pgp_keys: Option[List[String]] = None,
  root_token_pgp_key: Option[String] = None
)
object InitRequest {
  implicit val initRequestEncoder: Encoder[InitRequest] =
    deriveEncoder[InitRequest]
}

final case class InitResponse(keys: List[String], keys_base64: List[String], root_token: String)
object InitResponse {
  implicit val initResponseDecoder: Decoder[InitResponse] =
    deriveDecoder[InitResponse]
}

// https://www.vaultproject.io/api/system/seal-status.html
final case class SealStatusResponse(`sealed`: Boolean, t: Int, n: Int, progress: Int)
object SealStatusResponse {
  implicit val sealStatusResponseDecoder: Decoder[SealStatusResponse] =
    deriveDecoder[SealStatusResponse]
}

// https://www.vaultproject.io/api/system/unseal.html
final case class UnsealRequest(key: String, reset: Boolean = false, migrate: Boolean = false)
object UnsealRequest {
  implicit val unsealRequestEncoder: Encoder[UnsealRequest] =
    deriveEncoder[UnsealRequest]
}

final case class UnsealResponse(`sealed`: Boolean, t: Int, n: Int, progress: Int)
object UnsealResponse {
  implicit val unsealResponseDecoder: Decoder[UnsealResponse] =
    deriveDecoder[UnsealResponse]
}

sealed trait PluginType
final case class Auth() extends PluginType {
  override def toString: String = "auth"
}

final case class Database() extends PluginType {
  override def toString: String = "database"
}

final case class Secret() extends PluginType {
  override def toString: String = "secret"
}

object PluginType {
  implicit val pluginTypeEncoder: Encoder[PluginType] = { pt =>
    Encoder.encodeString(pt.toString)
  }
}

// https://www.vaultproject.io/api/system/plugins-catalog.html#register-plugin
final case class RegisterPluginRequest(
  sha256: String,
  command: String,
  args: Vector[String] = Vector.empty,
  env: Vector[String] = Vector.empty
)
object RegisterPluginRequest {
  implicit val registerPluginRequestEncoder: Encoder[RegisterPluginRequest] =
    deriveEncoder[RegisterPluginRequest]
}

// https://www.vaultproject.io/api/system/mounts.html#enable-secrets-engine
// TODO Implement rest of parameters
final case class EnableSecretsEngine(`type`: String)
object EnableSecretsEngine {
  implicit val enableSecretsEngineEncoder: Encoder[EnableSecretsEngine] =
    deriveEncoder[EnableSecretsEngine]
}

// https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#configauth_code_url
final case class AuthCodeUrlRequest(redirect_url: String, scopes: Vector[String], state: String)
object AuthCodeUrlRequest {
  implicit val authCodeUrlRequestEncoder: Encoder[AuthCodeUrlRequest] =
    Encoder.forProduct3("redirect_url", "scopes", "state") { r =>
      (r.redirect_url, r.scopes.mkString(" "), r.state)
    }
}

final case class AuthCodeUrlResponse(url: String)
object AuthCodeUrlResponse {
  implicit val authCodeUrlResponseDecoder: Decoder[AuthCodeUrlResponse] = (c: HCursor) =>
    c.downField("data").downField("url").as[String].map(AuthCodeUrlResponse(_))
}

// https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#put-write-2
final case class UpdateCredentialRequest(code: String, redirect_url: String)
object UpdateCredentialRequest {
  implicit val updateCredentialRequestEncoder: Encoder[UpdateCredentialRequest] = deriveEncoder[UpdateCredentialRequest]
}

// https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#get-read-1
final case class CredentialResponse(accessToken: String, expireTime: OffsetDateTime)
object CredentialResponse {
  implicit val credentialResponseDecoder: Decoder[CredentialResponse] =
    (c: HCursor) => {
      val data: ACursor = c.downField("data")
      for {
        token <- data.downField("access_token").as[String]
        expiration <- data.downField("expire_time").as[OffsetDateTime]
      } yield CredentialResponse(token, expiration)
    }
}

// https://www.vaultproject.io/api/secret/kv/kv-v2.html#read-secret-version
final case class KVMetadata(
  created_time: OffsetDateTime,
//                            deletion_time: Option[OffsetDateTime],
  destroyed: Boolean,
  version: Int
)
object KVMetadata {
  implicit val KVMetadataDecoder: Decoder[KVMetadata] =
    deriveDecoder[KVMetadata]
}

// https://www.vaultproject.io/api/secret/kv/kv-v2.html#create-update-secret
final case class CreateSecretRequest(cas: Option[Int], data: Json)
object CreateSecretRequest {
  implicit val createSecretRequestEncoder: Encoder[CreateSecretRequest] =
    Encoder.instance { req =>
      val optionsObj: JsonObject = req.cas
        .flatMap { casValue =>
          Some(JsonObject.singleton("options", Json.obj(("cas", Json.fromInt(casValue)))))
        }
        .getOrElse(JsonObject.empty)

      val dataObj: JsonObject = JsonObject.singleton("data", req.data)

      Json.fromJsonObject(optionsObj.deepMerge(dataObj))
    }
}

// https://www.vaultproject.io/api/secret/kv/kv-v2.html#read-secret-version
final case class KVGetResult(metadata: Option[KVMetadata], data: Json)
object KVGetResult {
  implicit val kvGetResultDecoder: Decoder[KVGetResult] = (c: HCursor) => {
    val data: ACursor = c.downField("data")
    for {
      metadata <- data.downField("metadata").as[Option[KVMetadata]]
      innerData <- data.downField("data").as[Json]
    } yield KVGetResult(metadata, innerData)
  }
}

final case class CreateOauthSecretResponse(data: Json)
object CreateOauthSecretResponse {
  implicit val createOauthSecretResponseDecoder: Decoder[CreateOauthSecretResponse] =
    deriveDecoder[CreateOauthSecretResponse]
}

final case class KVOauthGetResult(data: Json)
object KVOauthGetResult {
  implicit val kvOauthGetResultDecoder: Decoder[KVOauthGetResult] =
    (c: HCursor) => {
      val data: ACursor = c.downField("data")
      for {
        innerData <- data.as[Json]
      } yield KVOauthGetResult(innerData)
    }
}

final case class CreateUserRequest(password: String, policies: List[String])
object CreateUserRequest {
  implicit val createUserRequestEncoder: Encoder[CreateUserRequest] =
    Encoder.forProduct2("password", "policies")(req => (req.password, req.policies.mkString(",")))
}

final case class LoginResponse(token: String, accessorId: String, policies: List[String], metadata: Json)
object LoginResponse {
  implicit val loginResponse: Decoder[LoginResponse] = (c: HCursor) => {
    val auth: ACursor = c.downField("auth")
    for {
      token <- auth.get[String]("client_token")
      accessorId <- auth.get[String]("accessor")
      policies <- auth.get[List[String]]("policies")
      metadata <- auth.get[Json]("metadata")
    } yield LoginResponse(token, accessorId, policies, metadata)
  }
}
