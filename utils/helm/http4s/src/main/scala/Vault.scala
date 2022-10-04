package com.radix.utils.helm.http4s.vault

import cats.Applicative
import cats.effect.{ConcurrentEffect, Resource}
import cats.implicits._
import com.radix.utils.helm.vault._
import io.circe._
import io.circe.fs2.{byteStreamParser, decoder}
import io.circe.syntax._
import org.http4s.Method.{DELETE, GET, POST, PUT}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import shapeless.the

import java.net.ConnectException

class Vault[F[_]: ConcurrentEffect](authToken: F[Option[String]], baseUrl: Uri)(implicit
  blazeResource: Resource[F, Client[F]]
) extends VaultInterface[F] {

  def this(authToken: Option[String] = None, baseUrl: Uri)(implicit blazeResource: Resource[F, Client[F]]) {
    this(ConcurrentEffect[F].pure(authToken), baseUrl)
  }

  def this(authToken: String, baseUrl: Uri)(implicit blazeResource: Resource[F, Client[F]]) {
    this(Some(authToken), baseUrl)
  }

  val baseHeaders: F[List[Header]] = authToken.map {
    case Some(raw) => List(Header("X-Vault-Token", raw))
    case None      => List.empty
  }

  /**
   * Note: This function is only called if we have a response (i.e. a successful connection to the server was established
   * and we have a status code).
   */
  private def errorResponseHandler(response: Response[F]): F[Throwable] = {
    response.body
      .through(byteStreamParser)
      .through(decoder[F, VaultErrorResponseBody])
      .compile
      .last
      .map(_.map(body => VaultErrorResponse(body, response.status)))
      .map(_.getOrElse(VaultErrorMalformedResponse("No response")))
  }

  /** This is always evaluated when a failure occurs (irrespective of whether we have a response from the server). */
  private def errorHandler(exception: Throwable): VaultError =
    exception match {
      case _: ConnectException              => VaultConnectionError()
      case err: ParsingFailure              => VaultErrorMalformedResponse(err)
      case err: MalformedMessageBodyFailure => VaultErrorMalformedResponse(err)
      case err: InvalidMessageBodyFailure   => VaultErrorMalformedResponse(err)
      case ve: VaultError                   => ve
    }

  private def submitRequest[R](req: Request[F])(implicit d: Decoder[R]): F[Either[VaultError, R]] = {
    baseHeaders.flatMap { heads =>
      val trueRequest: Request[F] = req.withHeaders(heads: _*)

      blazeResource.use { client =>
        client
          .expectOr[R](trueRequest)(errorResponseHandler)(jsonOf[F, R])
          .attempt
          .map(_.leftMap(errorHandler))
      }
    }
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
  private def submitRequestNoResponse(req: Request[F]): F[Either[VaultError, Unit]] = {
    baseHeaders.flatMap { heads =>
      val trueRequest: Request[F] = req.withHeaders(heads: _*)

      blazeResource.use { client =>
        client.fetch(trueRequest) { response =>
          if (response.status.code != 200 && response.status.code != 204) {
            errorResponseHandler(response).map { err =>
              Left(errorHandler(err))
            }
          } else {
            the[Applicative[F]].pure(Right(()))
          }
        }
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

  private[helm] def setDatum[D: Encoder](path: String, value: D): F[Either[VaultError, Unit]] = {
    submitRequestNoResponse(
      Request[F](method = POST, uri = appendPath(baseUrl / "v1", path))
        .withEntity(value.asJson)
    )
  }

  private[helm] def setWithReply[D: Encoder, R: Decoder](path: String, value: D): F[Either[VaultError, R]] = {
    submitRequest[R](
      Request[F](method = POST, uri = appendPath(baseUrl / "v1", path))
        .withEntity(value.asJson)
    )
  }

  private[helm] def getDatum[R: Decoder](path: String): F[Either[VaultError, R]] = {
    submitRequest[R](
      Request[F](method = GET, uri = appendPath(baseUrl / "v1", path))
    )
  }

  override def sealStatus(): F[Either[VaultError, SealStatusResponse]] =
    submitRequest(Request[F](method = GET, uri = baseUrl / "v1" / "sys" / "seal-status"))

  override def sysInit(req: InitRequest): F[Either[VaultError, InitResponse]] =
    submitRequest(
      Request[F](method = POST, uri = baseUrl / "v1" / "sys" / "init").withEntity(req.asJson)
    )

  override def sysInitStatus: F[Either[VaultError, Boolean]] =
    submitRequest[io.circe.Json](Request[F](method = GET, uri = baseUrl / "v1" / "sys" / "init"))
      .map(_.flatMap(_.hcursor.downField("initialized").as[Boolean].leftMap(err => VaultErrorMalformedResponse(err))))

  override def unseal(req: UnsealRequest): F[Either[VaultError, UnsealResponse]] =
    submitRequest(
      Request[F](method = PUT, uri = baseUrl / "v1" / "sys" / "unseal").withEntity(req.asJson)
    )

  override def registerPlugin(
    pluginType: PluginType,
    name: String,
    req: RegisterPluginRequest
  ): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = PUT,
        uri = baseUrl / "v1" / "sys" / "plugins" / "catalog" / pluginType.toString / name
      ).withEntity(req.asJson)
    )

  override def enableSecretsEngine(pluginPath: String, req: EnableSecretsEngine): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](method = POST, uri = appendPath(baseUrl / "v1" / "sys" / "mounts", pluginPath))
        .withEntity(req.asJson)
    )

  override def authCodeUrl(pluginPath: String, req: AuthCodeUrlRequest): F[Either[VaultError, AuthCodeUrlResponse]] =
    submitRequest(
      Request[F](
        method = PUT,
        uri = appendPath(baseUrl / "v1", pluginPath) / "config" / "auth_code_url"
      ).withEntity(req.asJson)
    )

  override def updateOauthCredential(
    pluginPath: String,
    credentialName: String,
    req: UpdateCredentialRequest
  ): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = PUT,
        uri = appendPath(baseUrl / "v1", pluginPath) / "creds" / credentialName
      ).withEntity(req.asJson)
    )

  override def getOauthCredential(
    pluginPath: String,
    credentialName: String
  ): F[Either[VaultError, CredentialResponse]] =
    submitRequest(
      Request[F](
        method = GET,
        uri = appendPath(baseUrl / "v1", pluginPath) / "creds" / credentialName
      )
    )

  override def deleteOauthCredential(pluginPath: String, credentialName: String): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = DELETE,
        uri = appendPath(baseUrl / "v1", pluginPath) / "creds" / credentialName
      )
    )

  override def createSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "secret" / name).withEntity(req.asJson)
    )

  override def getSecret[R](name: String)(implicit d: Decoder[R]): F[Either[VaultError, KVGetResult[R]]] = {
    submitRequest[KVGetResult[R]](
      Request[F](method = GET, uri = baseUrl / "v1" / "secret" / name)
    )
  }

  override def deleteSecret(name: String): F[Either[VaultError, Unit]] = {
    submitRequestNoResponse(
      Request[F](method = DELETE, uri = baseUrl / "v1" / "secret" / name)
    )
  }

  def createOauthServer(
    pluginPath: String,
    name: String,
    req: CreateOauthServerRequest
  ): F[Either[VaultError, Unit]] = {
    submitRequestNoResponse(
      Request[F](method = PUT, uri = appendPath(baseUrl / "v1", pluginPath) / "servers" / name)
        .withEntity(req.asJson)
    )
  }

  override def deleteOauthServer(pluginPath: String, name: String): F[Either[VaultError, Unit]] =
    submitRequestNoResponse(
      Request[F](
        method = DELETE,
        uri = appendPath(baseUrl / "v1", pluginPath) / "servers" / name
      )
    )

  override def createUser(name: String, password: String, policies: List[String]): F[Either[VaultError, Unit]] = {
    val req = CreateUserRequest(password, policies)
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "auth" / "userpass" / "users" / name)
        .withEntity(req.asJson)
    )
  }

  override def login(username: String, password: String): F[Either[VaultError, LoginResponse]] = {
    val req = Map("password" -> password)
    submitRequest(
      Request[F](method = POST, uri = baseUrl / "v1" / "auth" / "userpass" / "login" / username)
        .withEntity(req.asJson)
    )
  }

  override def enableAuthMethod(authMethod: String): F[Either[VaultError, Unit]] = {
    val req = Map("type" -> authMethod)
    submitRequestNoResponse(
      Request[F](method = POST, uri = baseUrl / "v1" / "sys" / "auth" / authMethod)
        .withEntity(req.asJson)
    )
  }

  override def getCertificate(
    pkiName: String,
    commonName: String,
    ttl: String
  ): F[Either[VaultError, CertificateResponse]] = {
    val req = Map("common_name" -> commonName, "ttl" -> ttl, "exclude_cn_from_sans" -> "false", "format" -> "pem")
    for {
      headers <- baseHeaders
      request =
        Request[F](method = POST, uri = baseUrl / "v1" / pkiName / "issue" / "tls-cert", headers = Headers(headers))
          .withEntity(req.asJson)
      response <- submitRequest[CertificateResponse](request)
    } yield response
  }

  // the following methods are radix-specific, not vault-specific
  // TODO make a seperate interpreter just for radix stuff, createable from a VaultInterpreter rather than extending
  // VaultInteraface, think this is cleaner (@Jtrim777 or @liam923 pls)
  override def createEntity(
    name: String,
    policies: List[String],
    metadata: Map[String, Json]
  ): F[Either[VaultError, String]] = {
    val payload = Json.obj(
      "name" -> name.asJson,
      // The metadata needs to be Map[String, String]
      "metadata" -> metadata.view.mapValues(_.asJson.noSpaces).toMap.asJson,
      "policies" -> policies.asJson
    )

    setWithReply[Json, Json]("identity/entity", payload).map {
      case Right(value) =>
        val c = value.hcursor
        val decode = c.downField("data").downField("id").as[String]

        decode match {
          case Right(id) => Right(id)
          case Left(err) => Left(VaultErrorMalformedResponse(err))
        }
      case Left(err) => Left(err)
    }
  }

  override def aliasEntity(
    name: String,
    entityID: String,
    mountAccessor: String,
    metadata: Map[String, Json]
  ): F[Either[VaultError, Unit]] = {
    val payload = Json.obj(
      "name" -> name.asJson,
      "canonical_id" -> entityID.asJson,
      "custom_metadata" -> metadata.view.mapValues(_.asJson.noSpaces).toMap.asJson,
      "mount_accessor" -> mountAccessor.asJson
    )

    setDatum("identity/entity-alias", payload)
  }

  override def readEntity[T: Decoder](id: String): F[Either[VaultError, T]] = {
    getDatum[Json](s"identity/entity/id/$id").map { rez =>
      rez.flatMap { json =>
        json.hcursor.downField("data").as[T] match {
          case Right(value) => Right(value)
          case Left(err)    => Left(VaultErrorMalformedResponse(err))
        }
      }
    }
  }

  override def updateEntity(
    id: String,
    name: String,
    policies: List[String],
    metadata: Map[String, Json],
    disabled: Boolean
  ): F[Either[VaultError, Unit]] = {
    val json = Json.obj(
      "name" -> name.asJson,
      "metadata" -> metadata.view.mapValues(_.asJson.noSpaces).toMap.asJson,
      "policies" -> policies.asJson,
      "disabled" -> disabled.asJson
    )

    setDatum("identity/entity/id/" + id, json)
  }

  override def listEntities: F[Either[VaultError, List[String]]] = {
    val decoder: Decoder[List[String]] = (c: HCursor) => {
      c.downField("data").downField("keys").as[List[String]]
    }

    submitRequest[List[String]](
      Request[F](
        method = GET,
        uri = (baseUrl / "v1" / "identity" / "entity" / "id").withQueryParam("list", "true")
      )
    )(decoder).map {
      // It gives a 404 error if there are no entities
      case Left(VaultErrorResponse(_, Status.NotFound)) => Right(List.empty)
      case other                                        => other
    }
  }

}
