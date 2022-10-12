package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._

final case class NomadAllocationNetworkResourceReservedPort(label: String, port: Int)

object NomadAllocationNetworkResourceReservedPort {
  implicit def nomadAllocationNetworkResourceReservedPortDecoder: Decoder[NomadAllocationNetworkResourceReservedPort] =
    new Decoder[NomadAllocationNetworkResourceReservedPort] {
      override def apply(j: HCursor): Result[NomadAllocationNetworkResourceReservedPort] =
        for {
          label <- j.downField("Label").as[String]
          port <- j.downField("Value").as[Int]
        } yield NomadAllocationNetworkResourceReservedPort(label, port)
    }
}

final case class NomadAllocationNetworkResource(
  ip: String,
  reservedPorts: Vector[NomadAllocationNetworkResourceReservedPort],
)

object NomadAllocationNetworkResource {
  implicit def NomadAllocationResponseDecoder: Decoder[NomadAllocationNetworkResource] =
    new Decoder[NomadAllocationNetworkResource] {
      override def apply(j: HCursor): Result[NomadAllocationNetworkResource] =
        for {
          ip <- j.downField("IP").as[String]
          reservedPorts <- j.downField("ReservedPorts").as[Vector[NomadAllocationNetworkResourceReservedPort]]
        } yield NomadAllocationNetworkResource(ip, reservedPorts)
    }
}

final case class NomadAllocationResponse(networks: Vector[NomadAllocationNetworkResource])

object NomadAllocationResponse {
  implicit def NomadAllocationResponseDecoder: Decoder[NomadAllocationResponse] = new Decoder[NomadAllocationResponse] {
    override def apply(j: HCursor): Result[NomadAllocationResponse] = {
      j.downField("Resources")
        .downField("Networks")
        .as[Vector[NomadAllocationNetworkResource]]
        .map(NomadAllocationResponse(_))
    }
  }
}
