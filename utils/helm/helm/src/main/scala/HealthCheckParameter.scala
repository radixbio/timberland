package com.radix.utils.helm

import io.circe._
import io.circe.syntax._

/** Case class representing a health check as defined in the Register Service API calls */
final case class HealthCheckParameter(
  name: String,
  id: Option[String],
  interval: Option[Interval],
  notes: Option[String],
  deregisterCriticalServiceAfter: Option[Interval],
  serviceId: Option[String],
  initialStatus: Option[HealthStatus],
  http: Option[String],
  tlsSkipVerify: Option[Boolean],
  script: Option[String],
  dockerContainerId: Option[String],
  tcp: Option[String],
  ttl: Option[Interval],
)

object HealthCheckParameter {
  implicit def HealthCheckParameterEncoder: Encoder[HealthCheckParameter] = new Encoder[HealthCheckParameter] {
    final def apply(hcp: HealthCheckParameter): Json = {
      Json
        .obj(
          ("Name", hcp.name.asJson),
          ("CheckID", hcp.id.asJson),
          ("Notes", hcp.notes.asJson),
          ("DeregisterCriticalServiceAfter", hcp.deregisterCriticalServiceAfter.asJson),
          ("ServiceID", hcp.serviceId.asJson),
          ("Status", hcp.initialStatus.asJson),
          ("HTTP", hcp.http.asJson),
          ("TLSSkipVerify", hcp.tlsSkipVerify.asJson),
          ("Script", hcp.script.asJson),
          ("DockerContainerID", hcp.dockerContainerId.asJson),
          ("TCP", hcp.tcp.asJson),
          ("TTL", hcp.ttl.asJson),
        )
        .dropNullValues
    }
  }
}
