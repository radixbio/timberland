package com.radix.utils.helm

import java.util.UUID

import cats.data.NonEmptyList

import scala.concurrent.duration._

//import cats.effect.Effect
import cats.~>

trait ConsulInterface[F[_]] extends (ConsulOp ~> F) {

  val URL: String

  val accessToken: Option[String] = None
  val credentials: Option[(String, String)] = None

  def apply[A](op: ConsulOp[A]): F[A]

  def kvGet(
    key: Key,
    recurse: Option[Boolean],
    datacenter: Option[String],
    separator: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[KVGetResult]]]

  def kvGetRaw(
    key: Key,
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[Option[Array[Byte]]]]

  def kvSet(key: Key, value: Array[Byte]): F[Unit]
  def kvSet(key: Key, value: Array[Byte], session: UUID): F[Boolean]
  def kvSet(key: Key, last: KVGetResult, value: Array[Byte]): F[Boolean]
  def kvSet(key: Key, modifyIndex: Long, value: Array[Byte]): F[Boolean]

  def kvList(prefix: Key): F[Set[Key]]

  def kvDelete(key: Key): F[Unit]

  def healthChecksForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]]

  def healthChecksForNode(
    node: String,
    datacenter: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]]

  def healthChecksInState(
    state: HealthStatus,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]]

  def healthNodesForService(
    service: String,
    datacenter: Option[String],
    near: Option[String],
    nodeMeta: Option[String],
    tag: Option[String],
    passingOnly: Option[Boolean],
    index: Option[Long],
    wait: Option[Interval]
  ): F[QueryResponse[List[HealthNodesForServiceResponse]]]

  def agentRegisterService(
    service: String,
    id: Option[String],
    tags: Option[NonEmptyList[String]],
    address: Option[String],
    port: Option[Int],
    enableTagOverride: Option[Boolean],
    check: Option[HealthCheckParameter],
    checks: Option[NonEmptyList[HealthCheckParameter]]
  ): F[Unit]

  def agentDeregisterService(id: String): F[Unit]

  def agentListServices(): F[Map[String, ServiceResponse]]

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): F[Unit]
  import java.util.UUID
  def sessionCreate(
    name: String,
    dc: Option[String],
    lockdelay: Option[Duration],
    node: Option[String],
    checks: Option[NonEmptyList[HealthCheckParameter]],
    behavior: Option[String],
    ttl: Option[String]
  ): F[UUID]

  def getSessionInfo(session: UUID): F[SessionResponse]
}

/** A nice place to store the Consul response headers so we can pass them around */
case class ConsulHeaders(
  index: Long,
  lastContact: Long,
  knownLeader: Boolean
)
