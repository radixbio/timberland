package com.radix.utils.helm.http4s

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{Effect, Resource}
import cats.implicits._
import com.radix.utils.helm._
import com.radix.utils.helm.http4s.{util => helmUtil}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
//import journal.Logger
import logstage._
import org.http4s.Method.PUT
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.syntax.string.http4sStringSyntax
import io.circe._
import io.circe.syntax._
//import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe._

final class Http4sConsulClient[F[_]](
  val baseUri: Uri,
  override val accessToken: Option[String] = helmUtil.getTokenFromEnvVars(),
  override val credentials: Option[(String, String)] = None
)(implicit F: Effect[F], blaze: Resource[F, Client[F]])
    extends ConsulInterface[F] {

  override val URL: String = baseUri.renderString

  private[this] val dsl = new Http4sClientDsl[F] {}
  import dsl._

  private implicit val keysDecoder: EntityDecoder[F, List[String]] =
    jsonOf[F, List[String]]
  private implicit val listKvGetResultDecoder: EntityDecoder[F, List[KVGetResult]] = jsonOf[F, List[KVGetResult]]
  private implicit val listServicesDecoder: EntityDecoder[F, Map[String, ServiceResponse]] =
    jsonOf[F, Map[String, ServiceResponse]]
  private implicit val sessionResponseDecoder: EntityDecoder[F, SessionResponse] =
    jsonOf[F, SessionResponse]
  private implicit val listHealthChecksDecoder: EntityDecoder[F, List[HealthCheckResponse]] =
    jsonOf[F, List[HealthCheckResponse]]
  private implicit val listHealthNodesForServiceResponseDecoder: EntityDecoder[F, List[HealthNodesForServiceResponse]] =
    jsonOf[F, List[HealthNodesForServiceResponse]]
  private implicit val strDecoder: EntityDecoder[F, SessionRegisterResult] =
    jsonOf[F, SessionRegisterResult]
  private implicit val catalogListNodesForServiceResponseDecoder
    : EntityDecoder[F, List[CatalogListNodesForServiceResponse]] =
    jsonOf[F, List[CatalogListNodesForServiceResponse]]

  private val log = IzLogger(Log.Level.Crit)

  def apply[A](op: ConsulOp[A]): F[A] = op match {
    case ConsulOp.KVGet(key, recurse, datacenter, separator, index, wait) =>
      kvGet(key, recurse, datacenter, separator, index, wait): F[A]
    case ConsulOp.KVGetRaw(key, index, wait) => kvGetRaw(key, index, wait)
    case ConsulOp.KVSetWithSession(key, value, session) =>
      kvSet(key, value, session)
    case ConsulOp.KVSet(key, value)                             => kvSet(key, value)
    case ConsulOp.KVSetWithModifyIndex(key, modifyIndex, value) => kvSet(key, modifyIndex, value)
    case ConsulOp.KVSetCAS(key, last, value)                    => kvSet(key, last, value)
    case ConsulOp.KVListKeys(prefix)                            => kvList(prefix)
    case ConsulOp.KVDelete(key)                                 => kvDelete(key)
    case ConsulOp.HealthListChecksForService(service, datacenter, near, nodeMeta, index, wait) =>
      healthChecksForService(service, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListChecksForNode(node, datacenter, index, wait) =>
      healthChecksForNode(node, datacenter, index, wait)
    case ConsulOp.HealthListChecksInState(state, datacenter, near, nodeMeta, index, wait) =>
      healthChecksInState(state, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, wait) =>
      healthNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, wait)
    case ConsulOp.DeregisterHealthCheck(checkId) =>
      deregisterHealthCheck(checkId)
    case ConsulOp.AddHealthCheckForService(serviceId, hostname, healthCheckId, healthCheckName) =>
      addHealthCheckForService(serviceId, hostname, healthCheckId, healthCheckName)
    case ConsulOp.AgentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks) =>
      agentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks)
    case ConsulOp.AgentDeregisterService(service) =>
      agentDeregisterService(service)
    case ConsulOp.AgentListServices => agentListServices(): F[A]
    case ConsulOp.AgentEnableMaintenanceMode(id, enable, reason) =>
      agentEnableMaintenanceMode(id, enable, reason)
    case ConsulOp
          .SessionCreate(name, datacenter, lockDelay, node, checks, behavior, ttl) =>
      sessionCreate(name, datacenter, lockDelay, node, checks, behavior, ttl)
    case ConsulOp.CatalogListNodesForService(service, tag) =>
      catalogListNodesForService(service, tag)
  }

  /**
   * Many nomad jobs store a consul acl token in the "ACCESS_TOKEN" environment variable
   */
  private def addConsulToken(req: Request[F]): Request[F] = {
    val envAccessToken = System.getenv("ACCESS_TOKEN") match {
      case null  => None
      case token => Some(token)
    }
    accessToken.orElse(envAccessToken).fold(req)(tok => req.putHeaders(Header("X-Consul-Token", tok)))
  }

  private def addCreds(req: Request[F]): Request[F] =
    credentials.fold(req) { case (un, pw) =>
      req.putHeaders(Authorization(BasicCredentials(un, pw)))
    }

  /** A nice place to store the Consul response headers so we can pass them around */
  private case class ConsulHeaders(
    index: Long,
    lastContact: Long,
    knownLeader: Boolean
  )

  /** Helper function to get the value of a header out of a Response. Only used by extractConsulHeaders */
  private def extractHeaderValue(header: String, response: Response[F]): F[String] = {
    response.headers.get(header.ci) match {
      case Some(header) => F.pure(header.value)
      case None =>
        F.raiseError(new NoSuchElementException(s"Header not present in response: $header"))
    }
  }

  /** Helper function to get Consul GET request metadata from response headers */
  private def extractConsulHeaders(response: Response[F]): F[ConsulHeaders] = {
    for {
      index <- extractHeaderValue("X-Consul-Index", response).map(_.toLong)
      lastContact <- extractHeaderValue("X-Consul-LastContact", response).map(_.toLong)
      knownLeader <- extractHeaderValue("X-Consul-KnownLeader", response).map(_.toBoolean)
    } yield ConsulHeaders(index, lastContact, knownLeader)
  }

  /**
   * Encapsulates the functionality for parsing out the Consul headers from the HTTP response and decoding the JSON body.
   * Note: these headers are only present for a portion of the API.
   */
  private def extractQueryResponse[A](response: Response[F])(implicit d: EntityDecoder[F, A]): F[QueryResponse[A]] =
    response match {
      case Successful(_) =>
        for {
          headers <- extractConsulHeaders(response)
          decodedBody <- response.as[A]
        } yield {
          QueryResponse(decodedBody, headers.index, headers.knownLeader, headers.lastContact)
        }
      case failedResponse =>
        handleConsulErrorResponse(failedResponse).flatMap(F.raiseError)
    }

  private def handleConsulErrorResponse(response: Response[F]): F[Throwable] = {
    response
      .as[String]
      .map(errorMsg => {
        println(response)
        new RuntimeException("Got error response from Consul: " + errorMsg)
      })
  }

  def kvGet(
    key: Key,
    recurse: Option[Boolean],
    datacenter: Option[String],
    separator: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[KVGetResult]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(
        addConsulToken(
          Request(
            uri = (baseUri / "v1" / "kv" / key)
              .+??("recurse", recurse)
              .+??("dc", datacenter)
              .+??("separator", separator)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )
      response <- blaze.use { client =>
        client.fetch[QueryResponse[List[KVGetResult]]](req) { response: Response[F] =>
          response.status match {
            case status @ (Status.Ok | Status.NotFound) =>
              for {
                headers <- extractConsulHeaders(response)
                value <-
                  if (status == Status.Ok) response.as[List[KVGetResult]]
                  else F.pure(List.empty)
              } yield {
                QueryResponse(value, headers.index, headers.knownLeader, headers.lastContact)
              }
            case _ =>
              handleConsulErrorResponse(response).flatMap(F.raiseError)
          }
        }
      }
    } yield {
      log.debug(s"consul response for key get $key was $response")
      response
    }
  }

  def kvGetRaw(
    key: Key,
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[Option[Array[Byte]]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(
        addConsulToken(
          Request(
            uri = (baseUri / "v1" / "kv" / key)
              .+?("raw")
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )
      response <- blaze.use { client =>
        client.fetch[QueryResponse[Option[Array[Byte]]]](req) { response: Response[F] =>
          response.status match {
            case status @ (Status.Ok | Status.NotFound) =>
              for {
                headers <- extractConsulHeaders(response)
                value <-
                  if (status == Status.Ok)
                    response.body.compile.toVector
                      .map(_.toArray)
                      .map(Some(_)) //to[Array].map(Some(_))
                  else F.pure(None)
              } yield {
                QueryResponse(value, headers.index, headers.knownLeader, headers.lastContact)
              }
            case _ =>
              handleConsulErrorResponse(response).flatMap(F.raiseError)
          }
        }
      }
    } yield {
      log.debug(s"consul response for raw key get $key is $response")
      response
    }
  }

  def kvSet(key: Key, value: Array[Byte]): F[Unit] =
    for {
      cps <- F.delay(key.codePoints().toArray.mkString(""))
      vs <- F.delay(value.mkString(""))
      _ <- F.delay(log.debug(s"codepoints for key $cps"))
      _ <- F.delay(log.debug(s"setting consul key $key to $vs"))
      req <- PUT(value, uri = baseUri / "v1" / "kv" / key)
        .map(addConsulToken _)
        .map(addCreds _)
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield log.debug(s"setting consul key $key resulted in response $response")
  def kvSet(key: Key, last: KVGetResult, value: Array[Byte]): F[Boolean] =
    for {
      vs <- F.delay(value.mkString(""))
      _ <- F.delay(log.debug(s"setting consul key $key to $vs"))
      req <- PUT(value, uri = (baseUri / "v1" / "kv" / key).+?("cas", last.modifyIndex))
        .map(addConsulToken _)
        .map(addCreds _)
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"setting consul key $key resulted in response $response")
      response.stripLineEnd.toBoolean
    }
  def kvSet(key: Key, modifyIndex: Long, value: Array[Byte]): F[Boolean] =
    for {
      vs <- F.delay(value.mkString(""))
      _ <- F.delay(log.debug(s"setting consul key $key to $vs"))
      req <- PUT(value, uri = (baseUri / "v1" / "kv" / key).+?("cas", modifyIndex))
        .map(addConsulToken _)
        .map(addCreds _)
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"setting consul key $key resulted in response $response")
      response.stripLineEnd.toBoolean
    }
  def kvSet(key: Key, value: Array[Byte], session: UUID): F[Boolean] =
    for {
      vs <- F.delay(value.mkString(""))
      _ <- F.delay(log.debug(s"setting consul key $key to $vs"))
      req <- PUT(value, uri = (baseUri / "v1" / "kv" / key).+?("acquire", session.toString))
        .map(addConsulToken _)
        .map(addCreds _)
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"setting consul key $key resulted in response $response")
      response.stripLineEnd.toBoolean
    }

  def kvList(prefix: Key): F[Set[Key]] = {
    val req = addCreds(
      addConsulToken(
        Request(uri =
          (baseUri / "v1" / "kv" / prefix)
            .withQueryParam(QueryParam.fromKey("keys"))
        )
      )
    )

    for {
      _ <- F.delay(log.debug(s"listing key consul with the prefix: $prefix"))
      response <- blaze.use { client =>
        client.expectOr[List[String]](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"listing of keys: $response")
      response.toSet
    }
  }

  def kvDelete(key: Key): F[Unit] = {
    val req = addCreds(addConsulToken(Request(Method.DELETE, uri = baseUri / "v1" / "kv" / key)))

    for {
      _ <- F.delay(log.debug(s"deleting $key from the consul KV store"))
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield log.debug(s"response from delete: $response")
  }

  def healthChecksForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for service $service"))
      req = addCreds(
        addConsulToken(
          Request(
            uri = (baseUri / "v1" / "health" / "checks" / service)
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )
      response <- blaze.use { client =>
        client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
      }
    } yield {
      log.debug(s"health check response: $response")
      response
    }
  }

  /**
   * Creates JSON payload to add health check to a service
   * @param id the id of the new health check, must be unique
   * @param hostname hostname of the http server that accepts health checks
   * @param serviceId the id of the service to add health check to, this is service instance id (not just service name)
   * @return json payload to add health check
   */
  private def addCheckPayload(
    id: String,
    hostname: String,
    serviceId: String,
    healthCheckName: String,
    interval: FiniteDuration = 10.seconds,
    timeout: FiniteDuration = 1.seconds
  ): String = {
    val json = Json.obj(
      ("ID", Json.fromString(id)),
      ("ServiceID", Json.fromString(serviceId)),
      ("Name", Json.fromString(healthCheckName)),
      ("HTTP", Json.fromString(hostname)),
      ("Status", Json.fromString("passing")),
      ("FailuresBeforeCritical", Json.fromInt(3)),
      ("Method", Json.fromString("GET")),
      ("Interval", Json.fromString(s"${interval.toSeconds}s")),
      ("Timeout", Json.fromString(s"${timeout.toSeconds}s")),
      ("TLSSkipVerify", Json.fromBoolean(true))
    )
    log.debug(s"json to add health check for ${hostname}: ${json.spaces2}")
    json.spaces2
  }

  def addHealthCheckForService(
    serviceId: String,
    hostname: String,
    healthCheckId: String,
    healthCheckName: String
  ): F[Unit] = {
    for {
      _ <- F.delay(log.info(s"registering a health check for ${serviceId}"))
      req <- PUT(
        addCheckPayload(healthCheckId, hostname, serviceId, healthCheckName),
        baseUri / "v1" / "agent" / "check" / "register"
      ).map(addConsulToken)
        .map(addCreds)
      response <- blaze.use(client => client.expectOr[String](req)(handleConsulErrorResponse))
    } yield {
      log.info(s"registered health check for ${serviceId}")
    }
  }

  def catalogListNodesForService(
    service: String,
    tag: Option[String] = None
  ): F[List[CatalogListNodesForServiceResponse]] = {
    for {
      _ <- F.delay(log.debug(s"fetching service info from catalog for service: $service"))
      req = addCreds(
        addConsulToken(
          Request(uri =
            (baseUri / "v1" / "catalog" / "service" / service)
              .+??("tag", tag)
          )
        )
      )
      response <- blaze.use { client =>
        client.expectOr[List[CatalogListNodesForServiceResponse]](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"catalog service info response: $response")
      response
    }
  }

  def healthChecksForNode(
    node: String,
    datacenter: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for node $node"))
      req = addCreds(
        addConsulToken(
          Request(
            uri = (baseUri / "v1" / "health" / "node" / node)
              .+??("dc", datacenter)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )
      response <- blaze.use { client =>
        client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
      }
    } yield {
      log.debug(s"health checks for node response: $response")
      response
    }
  }

  def healthChecksInState(
    state: HealthStatus,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for service ${HealthStatus.toString(state)}"))
      req = addCreds(
        addConsulToken(
          Request(
            uri = (baseUri / "v1" / "health" / "state" / HealthStatus.toString(state))
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )
      response <- blaze.use { client =>
        client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
      }
    } yield {
      log.debug(s"health checks in state response: $response")
      response
    }
  }

  def healthNodesForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    tag: Option[String],
    passingOnly: Option[Boolean],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthNodesForServiceResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching nodes for service $service from health API"))
      req = addCreds(
        addConsulToken(
          Request(uri =
            (baseUri / "v1" / "health" / "service" / service)
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("tag", tag)
              .+??(
                "passing",
                passingOnly.filter(identity)
              ) // all values of passing parameter are treated the same by Consul
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString))
          )
        )
      )

      response <- blaze.use { client =>
        client.fetch[QueryResponse[List[HealthNodesForServiceResponse]]](req)(extractQueryResponse)
      }
    } yield {
      log.debug(s"health nodes for service response: $response")
      response
    }
  }

  def deregisterHealthCheck(checkId: String): F[Unit] = {
    for {
      _ <- F.delay(log.debug(s"registering a health check for ${checkId}"))
      req <- PUT("", baseUri / "v1" / "agent" / "check" / "deregister" / checkId)
        .map(addConsulToken)
        .map(addCreds)
      response <- blaze.use(client => client.expectOr[String](req)(handleConsulErrorResponse))
    } yield {
      log.debug("")
    }
  }

  def agentRegisterService(
    service: String,
    id: Option[String],
    tags: Option[NonEmptyList[String]],
    address: Option[String],
    port: Option[Int],
    enableTagOverride: Option[Boolean],
    check: Option[HealthCheckParameter],
    checks: Option[NonEmptyList[HealthCheckParameter]]
  ): F[Unit] = {
//    val json: Json =
//      ("Name" := service) ->:
//        ("ID" :=? id) ->?:
//        ("Tags" :=? tags.map(_.toList)) ->?:
//        ("Address" :=? address) ->?:
//        ("Port" :=? port) ->?:
//        ("EnableTagOverride" :=? enableTagOverride) ->?:
//        ("Check" :=? check) ->?:
//        ("Checks" :=? checks.map(_.toList)) ->?:
//        jEmptyObject
    val json: Json = Json
      .obj(
        ("Name", service.asJson),
        ("ID", id.asJson),
        ("Tags", tags.map(_.toList).asJson),
        ("Address", address.asJson),
        ("Port", port.asJson),
        ("EnableTagOverride", enableTagOverride.asJson),
        ("Check", check.asJson),
        ("Checks", checks.map(_.toList).asJson)
      )
      .dropNullValues
    for {
      _ <- F.delay(log.debug(s"registering $service with json: ${json.toString}"))
      req <- PUT(json.toString, baseUri / "v1" / "agent" / "service" / "register")
        .map(addConsulToken _)
        .map(addCreds _)
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield log.debug(s"registering service $service resulted in response $response")
  }

  def agentDeregisterService(id: String): F[Unit] = {
    val req = addCreds(
      addConsulToken(Request(Method.PUT, uri = (baseUri / "v1" / "agent" / "service" / "deregister" / id)))
    )
    for {
      _ <- F.delay(log.debug(s"deregistering service with id $id"))
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield log.debug(s"response from deregister: $response")
  }

  def agentListServices(): F[Map[String, ServiceResponse]] = {
    for {
      _ <- F.delay(log.debug(s"listing services registered with local agent"))
      req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "agent" / "services"))))
      services <- blaze.use { client =>
        client.expectOr[Map[String, ServiceResponse]](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"got services: $services")
      services
    }
  }

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): F[Unit] = {
    for {
      _ <- F.delay(log.debug(s"setting service with id $id maintenance mode to $enable"))
      req = addCreds(
        addConsulToken(
          Request(
            Method.PUT,
            uri = (baseUri / "v1" / "agent" / "service" / "maintenance" / id)
              .+?("enable", enable)
              .+??("reason", reason)
          )
        )
      )
      response <- blaze.use { client =>
        client.expectOr[String](req)(handleConsulErrorResponse)
      }
    } yield log.debug(s"setting maintenance mode for service $id to $enable resulted in $response")
  }

  import java.util.UUID

  import scala.concurrent.duration._
//  implicit val finiteDurationEncoder: EncodeJson[Duration] = EncodeJson(
//    duration => Json.jString(duration.toSeconds.toString + "s"))
  implicit val finiteDurationEncoder: Encoder[Duration] = new Encoder[Duration] {
    final def apply(duration: Duration): Json = { (duration.toSeconds.toString + "s").asJson }
  }
  def sessionCreate(
    name: String,
    dc: Option[String],
    lockdelay: Option[Duration],
    node: Option[String],
    checks: Option[NonEmptyList[HealthCheckParameter]],
    behavior: Option[String],
    ttl: Option[String]
  ): F[UUID] =
    for {
      _ <- F.delay(log.debug(s"PUTting $name in /session/create"))
      req <- PUT(
        {
//          val json: Json =
//            ("Name" := name) ->:
//              ("dc" :=? dc) ->?:
//              ("LockDelay" :=? lockdelay) ->?:
//              ("Node" :=? node) ->?:
//              ("Behavior" :=? behavior) ->?:
//              ("Checks" :=? checks.map(_.toList)) ->?:
//              jEmptyObject
          val json = Json.obj(
            ("Name", name.asJson),
            ("dc", dc.asJson),
            ("LockDelay", lockdelay.asJson),
            ("Node", node.asJson),
            ("Behavior", behavior.asJson),
            ("Checks", checks.map(_.toList).asJson),
            ("TTL", ttl.asJson)
          )
          json.dropNullValues.toString
        },
        uri = baseUri / "v1" / "session" / "create"
      ).map(addConsulToken _).map(addCreds _)
      resp <- blaze.use { client =>
        client.expectOr[SessionRegisterResult](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"setting consul service $name results in $resp")
      resp.id
    }
  def sessionLeaderUUID(
    name: String,
    dc: Option[String],
    lockdelay: Option[Duration],
    node: Option[String],
    checks: Option[NonEmptyList[HealthCheckParameter]],
    behavior: Option[String]
  ): F[UUID] =
    for {
      _ <- F.delay(log.debug(s"PUTting $name in /session/create"))
      req <- PUT(
        Json
          .obj(
            ("Name", name.asJson),
            ("dc", dc.asJson),
            ("LockDelay", lockdelay.asJson),
            ("Node", node.asJson),
            ("Behavior", behavior.asJson),
            ("Checks", checks.map(_.toList).asJson)
          )
          .dropNullValues
          .toString,
        baseUri / "v1" / "session" / "create"
      ).map(addConsulToken _).map(addCreds _)
      resp <- blaze.use { client =>
        client.expectOr[SessionRegisterResult](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"setting consul service $name results in $resp")
      resp.id
    }

  def getSessionInfo(session: UUID): F[SessionResponse] = {
    for {
      _ <- F.delay(log.debug(s"Getting session info from $session"))
      req <- F.delay(addCreds(addConsulToken(Request(uri = baseUri / "v1" / "session" / "info"))))
      resp <- blaze.use { client =>
        client.expectOr[SessionResponse](req)(handleConsulErrorResponse)
      }
    } yield {
      log.debug(s"Session info for session $session resulted in response $resp")
      resp
    }
  }
}
