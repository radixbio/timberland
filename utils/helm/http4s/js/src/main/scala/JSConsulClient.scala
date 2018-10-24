package helm
package js

import java.nio.ByteBuffer
import java.util.Base64

//import org.scalajs.dom
import cats.effect._
import cats.Monad
import cats.implicits._
import slogging.LazyLogging
import fr.hmil.roshttp.HttpRequest
import fr.hmil.roshttp.Method
import fr.hmil.roshttp.body.ByteBufferBody
import scalaz._
import Scalaz._
import cats.data.NonEmptyList
import monix.execution.Scheduler.Implicits.global

import fr.hmil.roshttp.response.SimpleHttpResponse
import argonaut._
import Argonaut._
//import std.tuple._

import scala.concurrent._
import scala.scalajs.niocharset.StandardCharsets

class JSConsulClient[F[_]](url: String)(implicit F: Effect[F])
    extends ConsulInterface[F]
    with LazyLogging {
  val URL = url

  override def apply[A](op: ConsulOp[A]): F[A] = op match {
    case ConsulOp.KVGet(key, recurse, datacenter, separator, index, wait) =>
      kvGet(key, recurse, datacenter, separator, index, wait)
    case ConsulOp.KVGetRaw(key, index, wait) => kvGetRaw(key, index, wait)
    case ConsulOp.KVSet(key, value)          => kvSet(key, value)
    case ConsulOp.KVListKeys(prefix)         => kvList(prefix)
    case ConsulOp.KVDelete(key)              => kvDelete(key)
    case ConsulOp.HealthListChecksForService(service,
                                             datacenter,
                                             near,
                                             nodeMeta,
                                             index,
                                             wait) =>
      healthChecksForService(service, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListChecksForNode(node, datacenter, index, wait) =>
      healthChecksForNode(node, datacenter, index, wait)
    case ConsulOp.HealthListChecksInState(state,
                                          datacenter,
                                          near,
                                          nodeMeta,
                                          index,
                                          wait) =>
      healthChecksInState(state, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListNodesForService(service,
                                            datacenter,
                                            near,
                                            nodeMeta,
                                            tag,
                                            passingOnly,
                                            index,
                                            wait) =>
      healthNodesForService(service,
                            datacenter,
                            near,
                            nodeMeta,
                            tag,
                            passingOnly,
                            index,
                            wait)
    case ConsulOp.AgentRegisterService(service,
                                       id,
                                       tags,
                                       address,
                                       port,
                                       enableTagOverride,
                                       check,
                                       checks) =>
      agentRegisterService(service,
                           id,
                           tags,
                           address,
                           port,
                           enableTagOverride,
                           check,
                           checks)
    case ConsulOp.AgentDeregisterService(service) =>
      agentDeregisterService(service)
    case ConsulOp.AgentListServices => agentListServices()
    case ConsulOp.AgentEnableMaintenanceMode(id, enable, reason) =>
      agentEnableMaintenanceMode(id, enable, reason)
  }


  private def extractConsulHeaders(
      resp: SimpleHttpResponse): Option[ConsulHeaders] = {
    val hdrs = resp.headers
    for {
      index <- hdrs.get("X-Consul-Index").map(_.toLong)
      lastContact <- hdrs.get("X-Consul-LastContact").map(_.toLong)
      knownLeader <- hdrs.get("X-Consul-KnownLeader").map(_.toBoolean)
    } yield ConsulHeaders(index, lastContact, knownLeader)
  }

  private def addConsulToken(req: HttpRequest): HttpRequest = {
    accessToken match {
      case Some(at) => req.withHeader("X-Consul-Token", at)
      case None =>
        logger.warn(
          "asked to provide consul token but couldn't since token is None")
        req
    }
  }
  private def addCreds(req: HttpRequest): HttpRequest = {
    credentials match {
      case Some((uname, pw)) =>
        val userPass = uname + ":" + pw
        val base64 = Base64.getEncoder.encodeToString(
          userPass.getBytes(StandardCharsets.ISO_8859_1))
        req.withHeader("Authorization", s"Basic $base64")
      case None =>
        logger.warn(
          "asked to add consul credentials but couldn't since credentials is None")
        req
    }
  }

  def kvGet(key: helm.Key,
            recurse: Option[Boolean],
            datacenter: Option[String],
            separator: Option[String],
            index: Option[Long],
            wait: Option[Interval]): F[QueryResponse[List[KVGetResult]]] = {
    val qparm = Seq(("recurse", recurse.map(_.toString)),
                    ("dc", datacenter),
                    ("separator", separator),
                    ("index", index.map(_.toString)),
                    ("wait", wait.map(Interval.toString))).flatMap({
      case tup =>
        ToTuple2Ops(tup)
        (Tuple2[String, String] _).lift[Option].tupled((Some(tup._1), tup._2))
    })
    val req: HttpRequest = addCreds(
      addConsulToken(
        HttpRequest()
          .withURL(URL + s"/v1/key/$key")
          .withQueryParameters(qparm: _*)))

    val resp: F[QueryResponse[List[KVGetResult]]] = for {
      _ <- F.delay(logger.debug(s"fetching consul key $key"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      res <- if (resp.statusCode == 200 || resp.statusCode == 404) {
        val queryResponse = for {
          value <- if (resp.statusCode == 200) {
            resp.body.decodeOption[List[KVGetResult]]
          } else Some(List.empty[KVGetResult])

          headers <- extractConsulHeaders(resp)
        } yield
          QueryResponse(value,
                        headers.index,
                        headers.knownLeader,
                        headers.lastContact)
        queryResponse match {
          case Some(elt) => F.pure(elt)
          case None =>
            F.raiseError(new RuntimeException(
              "could not decode or get headers from response: " + resp.toString))
        }
      } else {
        F.raiseError(
          new RuntimeException("got error from Consul: " + resp.toString))
      }
    } yield res
    resp
  }

  override def kvGetRaw(
      key: Key,
      index: Option[Long],
      wait: Option[Interval]): F[QueryResponse[Option[Array[Byte]]]] = {
    val qparm = Seq(("index", index.map(_.toString)),
                    ("wait", wait.map(Interval.toString)))
      .flatMap(
        tup =>
          (Tuple2[String, String] _)
            .lift[Option]
            .tupled((Some(tup._1), tup._2)))
    val req = addCreds(
      addConsulToken(
        HttpRequest()
          .withURL(URL + s"/v1/key/$key")
          .withQueryParameter("raw", "true") //TODO is this correct
          .withQueryParameters(qparm: _*)))

    val resp: F[QueryResponse[Option[Array[Byte]]]] = for {
      _ <- F.delay(logger.debug(s"fetching consul key $key"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      res <- if (resp.statusCode == 200 || resp.statusCode == 404) {
        for {
          headers <- F.pure(extractConsulHeaders(resp).get)
          value <- F.pure(if (resp.statusCode == 200) {
            Some(resp.body.getBytes)
          } else None)
        } yield
          QueryResponse(value,
                        headers.index,
                        headers.knownLeader,
                        headers.lastContact)
      } else {
        F.raiseError(
          new RuntimeException("got error response from Consul: " + resp))
      }
    } yield res
    resp
  }
  override def agentDeregisterService(id: String): F[Unit] = {
    val url = URL + "/v1/agent/service/deregister/" + id
    val req = addCreds(addConsulToken(HttpRequest().withURL(url)))
    for {
      _ <- F.delay(logger.debug(s"deregistering service with id $id"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
    } yield logger.debug(s"response from deregister $resp")
  }

  override def agentEnableMaintenanceMode(id: String,
                                          enable: JsonBoolean,
                                          reason: Option[String]): F[Unit] = {
    val url = URL + "/v1/agent/service/maintenance/" + id
    val qparm = Seq(("enable", Some(enable.toString)), ("reason", reason))
      .flatMap(
        tup =>
          (Tuple2[String, String] _)
            .lift[Option]
            .tupled((Some(tup._1), tup._2)))

    val req = addCreds(
      addConsulToken(HttpRequest().withURL(url).withQueryParameters(qparm: _*)))
    for {
      _ <- F.delay(
        logger.debug(s"setting service with $id maintenence mode to $enable"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
    } yield
      logger.debug(
        s"setting maintenence mode for service $id to $enable resulted in $resp")
  }

  override def agentListServices(): F[Map[String, ServiceResponse]] = {
    val url = URL + "/v1/agent/services"
    val req = HttpRequest().withURL(url)

    val servicesEither = for {
      _ <- F.delay(
        logger.debug(s"listing services registered with local agent"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      services = resp.body.decodeEither[Map[String, ServiceResponse]]

    } yield services
    servicesEither.flatMap({
      case Left(err) =>
        F.raiseError(
          new RuntimeException(
            s"error while decoding response body to list services $err"))
      case Right(succ) => F.pure(succ)
    })
  }
  override def agentRegisterService(
      service: String,
      id: Option[String],
      tags: Option[NonEmptyList[String]],
      address: Option[String],
      port: Option[Int],
      enableTagOverride: Option[Boolean],
      check: Option[HealthCheckParameter],
      checks: Option[NonEmptyList[HealthCheckParameter]]): F[Unit] = {

    val url = URL + "/v1/agent/service/register/"

    val json: Json =
      ("Name" := service) ->:
        ("ID" :=? id) ->?:
        ("Tags" :=? tags.map(_.toList)) ->?:
        ("Address" :=? address) ->?:
        ("Port" :=? port) ->?:
        ("EnableTagOverride" :=? enableTagOverride) ->?:
        ("Check" :=? check) ->?:
        ("Checks" :=? checks.map(_.toList)) ->?:
        jEmptyObject

    val req = addCreds(
      addConsulToken(
        HttpRequest()
          .withURL(url)
          .withMethod(Method.PUT)
          //TODO does this mangle
          .withBody(ByteBufferBody(ByteBuffer.wrap(json.toString.getBytes),
                                   contentType = "application/json"))))

    for {
      _ <- F.delay(
        logger.debug(s"registering $service with json: ${json.toString}"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
    } yield
      logger.debug(s"registering service $service resulted in response $resp")

  }
  override def healthChecksForNode(
      node: String,
      datacenter: Option[String],
      index: Option[Long],
      wait: Option[Interval]): F[QueryResponse[List[HealthCheckResponse]]] = {
    val url = URL + "/v1/health/node/" + node
    val qparm = Seq(("dc", datacenter),
                    ("index", index.map(_.toString)),
                    ("wait", wait.map(Interval.toString))).flatMap(
      tup =>
        (Tuple2[String, String] _)
          .lift[Option]
          .tupled((Some(tup._1), tup._2)))

    val req = HttpRequest().withURL(url).withQueryParameters(qparm: _*)
    val unhandled = for {
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      headers = extractConsulHeaders(resp)
      lol = resp.body.decodeOption[List[HealthCheckResponse]]
      qr <- F.pure(for {
        h <- headers
        v <- lol
      } yield QueryResponse(v, h.index, h.knownLeader, h.lastContact))
    } yield qr

    unhandled.flatMap({
      case Some(resp) => F.pure(resp)
      case None =>
        F.raiseError(
          new RuntimeException(
            "could not decode body or extract consul headers"))
    })

  }
  override def healthChecksForService(
      service: String,
      datacenter: Option[String],
      near: Option[String],
      nodeMeta: Option[String],
      index: Option[Long],
      wait: Option[Interval]): F[QueryResponse[List[HealthCheckResponse]]] = {
    val url = URL + "/v1/health/checks/" + service
    val qparm = Seq(("dc", datacenter),
                    ("near", near),
                    ("node-meta", nodeMeta),
                    ("index", index.map(_.toString)),
                    ("wait", wait.map(Interval.toString))).flatMap(
      tup =>
        (Tuple2[String, String] _)
          .lift[Option]
          .tupled((Some(tup._1), tup._2)))

    val req = HttpRequest().withURL(url).withQueryParameters(qparm: _*)

    for {
      _ <- F.delay(logger.debug(s"fetching health checks for service $service"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      res <- resp.statusCode match {
        case 200 =>
          val decoded = resp.body.decodeEither[List[HealthCheckResponse]]
          val headers = extractConsulHeaders(resp)
          decoded match {
            case Left(err) =>
              F.raiseError(
                new RuntimeException(
                  "could not decode body in healthChecksForService: " + err))
            case Right(res) =>
              headers match {
                case Some(header) =>
                  F.pure(
                    QueryResponse(res,
                                  header.index,
                                  header.knownLeader,
                                  header.lastContact))
                case None =>
                  F.raiseError(
                    new RuntimeException(
                      "could not extract headers from Consul message"))
              }
          }
        case ecode =>
          F.raiseError(
            new RuntimeException("Consul responded with error code: " + ecode))
      }
    } yield res

  }
  override def healthChecksInState(
      state: HealthStatus,
      datacenter: Option[String],
      near: Option[String],
      nodeMeta: Option[String],
      index: Option[Long],
      wait: Option[Interval]): F[QueryResponse[List[HealthCheckResponse]]] = {
    val url = URL + "/v1/health/state/" + HealthStatus.toString(state)
    val qparm = Seq(("dc", datacenter),
                    ("near", near),
                    ("node-meta", nodeMeta),
                    ("index", index.map(_.toString)),
                    ("wait", wait.map(Interval.toString))).flatMap(
      tup =>
        (Tuple2[String, String] _)
          .lift[Option]
          .tupled((Some(tup._1), tup._2)))

    val req = HttpRequest().withURL(url).withQueryParameters(qparm: _*)

    for {
      _ <- F.delay(logger.debug(
        s"fetching health checks for service ${HealthStatus.toString(state)}"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      res <- resp.statusCode match {
        case 200 =>
          val decoded = resp.body.decodeEither[List[HealthCheckResponse]]
          val headers = extractConsulHeaders(resp)

          decoded match {
            case Left(err) =>
              F.raiseError(
                new RuntimeException(
                  "could not decode body in healthChecksForService: " + err))
            case Right(res) =>
              headers match {
                case Some(header) =>
                  F.pure(
                    QueryResponse(res,
                                  header.index,
                                  header.knownLeader,
                                  header.lastContact))
                case None =>
                  F.raiseError(
                    new RuntimeException(
                      "could not extract headers from Consul message"))
              }
          }
        case ecode =>
          F.raiseError(
            new RuntimeException("Consul responded with error code: " + ecode))
      }
    } yield res
  }
  override def healthNodesForService(service: String,
                                     datacenter: Option[String],
                                     near: Option[String],
                                     nodeMeta: Option[String],
                                     tag: Option[String],
                                     passingOnly: Option[Boolean],
                                     index: Option[Long],
                                     wait: Option[Interval])
    : F[QueryResponse[List[HealthNodesForServiceResponse]]] = {
    val url = URL + "/v1/health/service/" + service
    val qparm = Seq(
      ("dc", datacenter),
      ("near", near),
      ("node-meta", nodeMeta),
      ("tag", tag),
      ("passing", passingOnly.filter(identity).map(_.toString)),
      ("index", index.map(_.toString)),
      ("wait", wait.map(Interval.toString))
    ).flatMap(
      tup =>
        (Tuple2[String, String] _)
          .lift[Option]
          .tupled((Some(tup._1), tup._2)))

    val req = HttpRequest()
      .withURL(url)
      .withQueryParameters(qparm: _*)

    for {
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      decoded = resp.body.decodeEither[List[HealthNodesForServiceResponse]]
      headers = extractConsulHeaders(resp)
      d <- decoded match {
        case Right(elt) => F.pure(elt)
        case Left(err) =>
          F.raiseError(new RuntimeException(
            s"could not decode healthnodes response in function healthNodesForService: $err"))
      }
      h <- headers match {
        case Some(elt) => F.pure(elt)
        case None =>
          F.raiseError(
            new RuntimeException(
              s"could not extract consul headers from response $resp"))
      }
    } yield QueryResponse(d, h.index, h.knownLeader, h.lastContact)

  }

  override def kvDelete(key: Key): F[Unit] = {
    val url = URL + "/v1/kv/" + key
    val req = addCreds(
      addConsulToken(HttpRequest().withURL(url).withMethod(Method.DELETE)))
    for {
      _ <- F.delay(logger.debug(s"deleting $key from the consul KV store"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
    } yield logger.debug(s"response from delete: $resp")
  }

  override def kvList(prefix: Key): F[Set[Key]] = {
    val url = URL + "/v1/kv/" + prefix
    val req = addCreds(
      addConsulToken(
        HttpRequest()
          .withURL(url)
          .withQueryString("keys") //TODO is this correct
      ))
    for {
      _ <- F.delay(logger.debug(s"listing key consul with the prefix: $prefix"))
      resp <- F.liftIO(IO.fromFuture(IO(req.send())))
      decoded = resp.body.decodeEither[List[String]]
      res <- decoded match {
        case Left(err) =>
          F.raiseError(
            new RuntimeException("could not decode body in kvList: " + err))
        case Right(res) => F.pure(res.toSet)
      }
    } yield res
  }

  override def kvSet(key: Key, value: Array[Byte]): F[Unit] = {
    val url = URL + "/v1/kv/" + key
    val req = HttpRequest()
      .withURL(url)
      .withMethod(Method.PUT)
    for {
      _ <- F.delay(logger.debug(s"setting consul key $key to $value"))
      resp <- F.liftIO(
        IO.fromFuture(IO(req.put(ByteBufferBody(ByteBuffer.wrap(value))))))

    } yield logger.debug(s"setting consul key $key resulted in response $resp")
  }
}
