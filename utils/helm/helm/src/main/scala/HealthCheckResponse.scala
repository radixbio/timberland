package com.radix.utils.helm

import io.circe._

/** Case class representing a health check as returned from an API call to Consul */
final case class HealthCheckResponse(
  node: String,
  checkId: String,
  name: String,
  status: HealthStatus,
  notes: String,
  output: String,
  serviceId: String,
  serviceName: String,
  serviceTags: List[String],
  createIndex: Long,
  modifyIndex: Long,
)

object HealthCheckResponse {
  implicit def HealthCheckResponseDecoder: Decoder[HealthCheckResponse] = new Decoder[HealthCheckResponse] {
    final def apply(j: HCursor): Decoder.Result[HealthCheckResponse] = {
      for {
        node <- j.downField("Node").as[String]
        checkId <- j.downField("CheckID").as[String]
        name <- j.downField("Name").as[String]
        status <- j.downField("Status").as[HealthStatus]
        notes <- j.downField("Notes").as[String]
        output <- j.downField("Output").as[String]
        serviceId <- j.downField("ServiceID").as[String]
        serviceName <- j.downField("ServiceName").as[String]
        serviceTags <- j.downField("ServiceTags").as[List[String]]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
      } yield HealthCheckResponse(
        node,
        checkId,
        name,
        status,
        notes,
        output,
        serviceId,
        serviceName,
        serviceTags,
        createIndex,
        modifyIndex,
      )
    }
  }
}
