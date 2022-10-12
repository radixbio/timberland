package com.radix.utils.helm

import scala.collection.immutable.{Set => SSet}
import io.circe._
import io.circe.syntax._
import io.circe.parser.parse
import cats.data.NonEmptyList
import cats.free.Free
import cats.free.Free.liftF

import scala.concurrent.duration._
import java.util.UUID

sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class KVGet(
    key: Key,
    recurse: Option[Boolean] = None,
    datacenter: Option[String] = None,
    separator: Option[String] = None,
    index: Option[Long] = None,
    maxWait: Option[Interval] = None,
  ) extends ConsulOp[QueryResponse[List[KVGetResult]]]

  final case class KVGetRaw(
    key: Key,
    index: Option[Long],
    maxWait: Option[Interval],
  ) extends ConsulOp[QueryResponse[Option[Array[Byte]]]]

  final case class KVSetWithSession(key: Key, value: Array[Byte], session: UUID) extends ConsulOp[Boolean]
  final case class KVSet(key: Key, value: Array[Byte]) extends ConsulOp[Unit]
  final case class KVSetWithModifyIndex(key: Key, modifyIndex: Long, value: Array[Byte]) extends ConsulOp[Boolean]
  final case class KVSetCAS(key: Key, last: KVGetResult, value: Array[Byte]) extends ConsulOp[Boolean]
  final case class KVDelete(key: Key) extends ConsulOp[Unit]

  final case class KVListKeys(prefix: Key) extends ConsulOp[SSet[String]]

  final case class HealthListChecksForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  final case class HealthListChecksForNode(
    node: String,
    datacenter: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  final case class HealthListChecksInState(
    state: HealthStatus,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  // There's also a Catalog function called List Nodes for Service
  final case class HealthListNodesForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    tag: Option[String],
    passingOnly: Option[Boolean],
    index: Option[Long],
    maxWait: Option[Interval],
  ) extends ConsulOp[QueryResponse[List[HealthNodesForServiceResponse]]]

  // adds a health check with id healthCheckId to service instance with id serviceId,
  // hostname is where to send health requests to
  final case class AddHealthCheckForService(
    serviceId: String,
    hostname: String,
    healthCheckId: String,
    healthCheckName: String,
  ) extends ConsulOp[Unit]

  // removes health check with the id given.
  final case class DeregisterHealthCheck(
    checkId: String
  ) extends ConsulOp[Unit]

  final case object AgentGetInfo extends ConsulOp[AgentInfoResult]
  final case object AgentListServices extends ConsulOp[Map[String, ServiceResponse]]
  final case class AgentSetToken(tokenType: String, token: String) extends ConsulOp[Unit]

  final case object AclGetTokens extends ConsulOp[List[AclTokenResult]]

  final case class CatalogListNodesForService(service: String, tag: Option[String])
      extends ConsulOp[List[CatalogListNodesForServiceResponse]]
  object CatalogListNodesForService {
    def apply(service: String): CatalogListNodesForService = new CatalogListNodesForService(service, None)
  }

  final case class AgentRegisterService(
    service: String,
    id: Option[String],
    tags: Option[NonEmptyList[String]],
    address: Option[String],
    port: Option[Int],
    enableTagOverride: Option[Boolean],
    check: Option[HealthCheckParameter],
    checks: Option[NonEmptyList[HealthCheckParameter]],
  ) extends ConsulOp[Unit]

  final case class AgentDeregisterService(id: String) extends ConsulOp[Unit]

  final case class AgentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String])
      extends ConsulOp[Unit]

  final case class KeyringInstallKey(str: String) extends ConsulOp[Unit]

  final case class KeyringSetPrimaryKey(str: String) extends ConsulOp[Unit]

  type ConsulOpF[A] = Free[ConsulOp, A]

  def kvGet(
    key: Key,
    recurse: Option[Boolean] = Some(false),
    datacenter: Option[String] = None,
    separator: Option[String] = None,
    index: Option[Long] = None,
    maxWait: Option[Interval] = None,
  ): ConsulOpF[QueryResponse[List[KVGetResult]]] =
    liftF(KVGet(key, recurse, datacenter, separator, index, maxWait))

  def kvGetRaw(
    key: Key,
    index: Option[Long],
    maxWait: Option[Interval],
  ): ConsulOpF[QueryResponse[Option[Array[Byte]]]] =
    liftF(KVGetRaw(key, index, maxWait))

  def kvGetJson[A](
    key: Key,
    index: Option[Long],
    maxWait: Option[Interval],
  )(implicit decoder: Decoder[A]): ConsulOpF[Either[ParsingFailure, QueryResponse[Option[A]]]] =
    kvGetRaw(key, index, maxWait).map { response =>
      response.value match {
        case Some(bytes) =>
          parse(new String(bytes, "UTF-8")).right.map(decoded => response.copy(value = decoded.as[A].toOption))
        case None =>
          Right(response.copy(value = None))
      }
    }

  def kvSet(key: Key, value: Array[Byte], session: UUID): ConsulOpF[Boolean] =
    liftF(KVSetWithSession(key, value, session))
  def kvSet(key: Key, value: Array[Byte]): ConsulOpF[Unit] =
    liftF(KVSet(key, value))
  def kvSet(key: Key, last: KVGetResult, value: Array[Byte]): ConsulOpF[Boolean] =
    liftF(KVSetCAS(key, last, value))
  def kvSet(key: Key, modifyIndex: Long, value: Array[Byte]): ConsulOpF[Boolean] =
    liftF(KVSetWithModifyIndex(key, modifyIndex, value))

  def kvSetJson[A](key: Key, value: A, session: UUID)(implicit A: Encoder[A]): ConsulOpF[Boolean] =
    kvSet(key, A(value).toString.getBytes("UTF-8"), session)
  def kvSetJson[A](key: Key, value: A)(implicit A: Encoder[A]): ConsulOpF[Unit] =
    kvSet(key, A(value).toString.getBytes("UTF-8"))
  def kvSetJson[A](key: Key, last: KVGetResult, value: A)(implicit A: Encoder[A]): ConsulOpF[Boolean] =
    kvSet(key, last, A(value).toString.getBytes("UTF-8"))
  def kvSetJson[A](key: Key, last: Long, value: A)(implicit A: Encoder[A]): ConsulOpF[Boolean] =
    kvSet(key, last, A(value).toString.getBytes("UTF-8"))

  def kvDelete(key: Key): ConsulOpF[Unit] =
    liftF(KVDelete(key))

  def kvListKeys(prefix: Key): ConsulOpF[SSet[String]] =
    liftF(KVListKeys(prefix))

  def healthListChecksForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksForService(service, datacenter, near, nodeMeta, index, maxWait))

  def healthListChecksForNode(
    node: String,
    datacenter: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksForNode(node, datacenter, index, maxWait))

  def healthListChecksInState(
    state: HealthStatus,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    maxWait: Option[Interval],
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksInState(state, datacenter, near, nodeMeta, index, maxWait))

  def healthListNodesForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    tag: Option[String],
    passingOnly: Option[Boolean],
    index: Option[Long],
    maxWait: Option[Interval],
  ): ConsulOpF[QueryResponse[List[HealthNodesForServiceResponse]]] =
    liftF(HealthListNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, maxWait))

  def agentListServices(): ConsulOpF[Map[String, ServiceResponse]] =
    liftF(AgentListServices)

  def catalogListNodesForService(service: String): ConsulOpF[List[CatalogListNodesForServiceResponse]] =
    liftF(CatalogListNodesForService(service))

  def agentRegisterService(
    service: String,
    id: Option[String],
    tags: Option[NonEmptyList[String]],
    address: Option[String],
    port: Option[Int],
    enableTagOverride: Option[Boolean],
    check: Option[HealthCheckParameter],
    checks: Option[NonEmptyList[HealthCheckParameter]],
  ): ConsulOpF[Unit] =
    liftF(AgentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks))

  def agentDeregisterService(id: String): ConsulOpF[Unit] =
    liftF(AgentDeregisterService(id))

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): ConsulOpF[Unit] =
    liftF(AgentEnableMaintenanceMode(id, enable, reason))

  def keyringInstallKey(key: String): ConsulOpF[Unit] =
    liftF(KeyringInstallKey(key))

  def keyringSetPrimaryKey(key: String): ConsulOpF[Unit] =
    liftF(KeyringSetPrimaryKey(key))

  final case class SessionCreate(
    name: String,
    dc: Option[String] = Some(""),
    lockDelay: Option[Duration] = Some(Duration.Inf),
    node: Option[String] = Some(""),
    checks: Option[NonEmptyList[HealthCheckParameter]] = None,
    behavior: Option[String] = Some("release"),
    ttl: Option[String] = None,
  ) extends ConsulOp[UUID]

  def sessionCreate(
    name: String,
    dc: Option[String] = Some(""),
    lockDelay: Option[Duration] = None,
    node: Option[String] = None,
    checks: Option[NonEmptyList[HealthCheckParameter]] = None,
    behavior: Option[String] = Some("release"),
    ttl: Option[String] = None,
  ): ConsulOpF[UUID] =
    liftF(SessionCreate(name, dc, lockDelay, node, checks, behavior, ttl))

}
