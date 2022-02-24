package com.radix.utils.helm.http4s.vault

import java.net.ConnectException

import cats.Applicative
import cats.implicits._
import cats.effect.{ConcurrentEffect, Resource}
import io.circe.syntax._
import org.http4s._
import org.http4s.Method.{GET, POST, PUT}
import org.http4s.circe._
import io.circe.{Decoder, ParsingFailure}
import io.circe.fs2.{byteStreamParser, decoder}
import org.http4s.client.Client
import com.radix.utils.helm.vault._
import shapeless.the

class Vault[F[_]: ConcurrentEffect](authToken: Option[String], baseUrl: Uri)(implicit
  blazeResource: Resource[F, Client[F]]
) extends VaultInterface[F] {
  val baseHeaders: Headers = authToken
    .map { tok =>
      Headers.of(Header("X-Vault-Token", tok))
    }
    .getOrElse(Headers.of())

  /**
   * Note: This function is only called if we have a response (i.e. a successful connection to the server was established
   * and we have a status code).
   */
  private def errorResponseHandler(response: Response[F]): F[Throwable] =
    response.body
      .through(byteStreamParser)
      .through(decoder[F, VaultErrorResponse])
      .compile
      .last
      .map(_.getOrElse(VaultErrorMalformedResponse()))

  /** This is always evaluated when a failure occurs (irrespective of whether we have a response from the server). */
  private def errorHandler(exception: Throwable): VaultError = exception match {
    case _: ConnectException            => VaultConnectionError()
    case _: ParsingFailure              => VaultErrorMalformedResponse()
    case _: MalformedMessageBodyFailure => VaultErrorMalformedResponse()
    case _: InvalidMessageBodyFailure   => VaultErrorMalformedResponse()
    case ve: VaultError                 => ve
  }

  private def submitRequest[R](req: Request[F])(implicit d: Decoder[R]): F[Either[VaultError, R]] =
    blazeResource.use { client =>
      client
        .expectOr[R](req)(errorResponseHandler)(jsonOf[F, R])
        .attempt
        .map(_.leftMap(errorHandler))
    }

  /**
   * We're not expecting a response (we are expecting a 204 No Content). When I tried to decode to Unit I received
   * the following error at runtime:
   *
   * java.lang.NoSuchMethodError: fs2.Pull$.stream$extension(Lfs2/internal/FreeC;)Lfs2/internal/FreeC;
   *
   * An unsuccessful response could have a response body with useful information. This function could be improved upon
   * by packaging the error response up and returning it to the caller. Currently it just returns a VaultErrorMalformedResponse.
   */
  private def submitRequestNoResponse(req: Request[F]): F[Either[VaultError, Unit]] =
    blazeResource.use { client =>
      client.fetch(req) { response =>
        if (response.status.code != 200 && response.status.code != 204) {
          errorResponseHandler(response).map { err =>
            Left(errorHandler(err))
          }
        } else {
          the[Applicative[F]].pure(Right(()))
        }
      }
    }

  /**
   * This is needed for plugin paths containing a slash. Without it, the
   * path will be transformed from, say, "foo/bar" to foo%2Fbar", which is
   * not what we want.
   */
  private def appendPath(base: Uri, path: Uri.Path): Uri =
    path.split("/").foldLeft(base)((accum, nextSegment) => accum / nextSegment)

  override def sealStatus: F[Either[VaultError, SealStatusResponse]] =
    submitRequest(Request[F](method = GET, uri = baseUrl / "v1" / "sys" / "seal-status", headers = baseHeaders))

  override def sysInit(req: InitRequest): F[Either[VaultError, InitResponse]] =
    submitRequest(
      Request[F](method = POST, uri = baseUrl / "v1" / "sys" / "init", headers = baseHeaders).withEntity(req.asJson)
    )

  override def sysInitStatus: F[Either[VaultError, Boolean]] =
    submitRequest[io.circe.Json](Request[F](method = GET, uri = baseUrl / "v1" / "sys" / "init", headers = baseHeaders))
      .map(_.flatMap(_.hcursor.downField("initialized").as[Boolean].leftMap(_ => VaultErrorMalformedResponse())))

  override def unseal(req: UnsealRequest): F[Either[VaultError, UnsealResponse]] =
    submitRequest(
      Request[F](method = PUT, uri = baseUrl / "v1" / "sys" / "unseal", headers = baseHeaders).withEntity(req.asJson)
    )

  override def registerPlugin(
    pluginType: PluginType,
    name: String,
    req: RegisterPluginRequest
  ): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = PUT,
        uri = baseUrl / "v1" / "sys" / "plugins" / "catalog" / pluginType.toString / name,
        headers = baseHeaders
      ).withEntity(req.asJson)
    )

  override def enableSecretsEngine(pluginPath: String, req: EnableSecretsEngine): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](method = POST, uri = appendPath(baseUrl / "v1" / "sys" / "mounts", pluginPath), headers = baseHeaders)
        .withEntity(req.asJson)
    )

  override def authCodeUrl(pluginPath: String, req: AuthCodeUrlRequest): F[Either[VaultError, AuthCodeUrlResponse]] =
    submitRequest(
      Request[F](
        method = PUT,
        uri = appendPath(baseUrl / "v1", pluginPath) / "config" / "auth_code_url",
        headers = baseHeaders
      ).withEntity(req.asJson)
    )

  override def updateCredential(
    pluginPath: String,
    credentialName: String,
    req: UpdateCredentialRequest
  ): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = PUT,
        uri = appendPath(baseUrl / "v1", pluginPath) / "creds" / credentialName,
        headers = baseHeaders
      ).withEntity(req.asJson)
    )

  override def getCredential(pluginPath: String, credentialName: String): F[Either[VaultError, CredentialResponse]] =
    submitRequest(
      Request[F](
        method = GET,
        uri = appendPath(baseUrl / "v1", pluginPath) / "creds" / credentialName,
        headers = baseHeaders
      )
    )

  override def createSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "secret" / name, headers = baseHeaders).withEntity(req.asJson)
    )

  override def getSecret[R](name: String)(implicit d: Decoder[R]): F[Either[VaultError, KVGetResult[R]]] = {
    submitRequest[KVGetResult[R]](Request[F](method = GET, uri = baseUrl / "v1" / "secret" / name, headers = baseHeaders))
  }

  override def createOauthSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](method = PUT, uri = appendPath(baseUrl / "v1", name), headers = baseHeaders)
        .withEntity(req.data.asJson)
    )

  override def getOauthSecret(name: String): F[Either[VaultError, KVOauthGetResult]] = {
    submitRequest(Request[F](method = GET, uri = appendPath(baseUrl / "v1", name), headers = baseHeaders))
  }

  override def createUser(name: String, password: String, policies: List[String]): F[Either[VaultError, Unit]] = {
    val req = CreateUserRequest(password, policies)
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "auth" / "userpass" / "users" / name, headers = baseHeaders)
        .withEntity(req.asJson)
    )
  }

  override def login(username: String, password: String): F[Either[VaultError, LoginResponse]] = {
    val req = Map("password" -> password)
    submitRequest(
      Request[F](method = POST, uri = baseUrl / "v1" / "auth" / "userpass" / "login" / username, headers = baseHeaders)
        .withEntity(req.asJson)
    )
  }

  override def enableAuthMethod(authMethod: String): F[Either[VaultError, Unit]] = {
    val req = Map("type" -> authMethod)
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "sys" / "auth" / authMethod, headers = baseHeaders)
        .withEntity(req.asJson)
    )
  }
}
