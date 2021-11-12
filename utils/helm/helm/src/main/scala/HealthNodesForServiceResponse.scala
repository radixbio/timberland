package com.radix.utils.helm

import io.circe._

/** Case class representing the response to the Health API's List Nodes For Service function */
final case class HealthNodesForServiceResponse(
  node: NodeResponse,
  service: ServiceResponse,
  checks: List[HealthCheckResponse]
)

object HealthNodesForServiceResponse {
  implicit def HealthNodesForServiceResponseDecoder: Decoder[HealthNodesForServiceResponse] =
    new Decoder[HealthNodesForServiceResponse] {
      final def apply(j: HCursor): Decoder.Result[HealthNodesForServiceResponse] = {
        for {
          node <- j.downField("Node").as[NodeResponse]
          service <- j.downField("Service").as[ServiceResponse]
          checks <- j.downField("Checks").as[List[HealthCheckResponse]]
        } yield HealthNodesForServiceResponse(node, service, checks)
      }
    }
}
