package com.radix.utils.helm

import io.circe._

/** Case class representing a service as returned from an API call to Consul */
final case class ServiceResponse(
  service: String,
  id: String,
  tags: List[String],
  address: String,
  port: Int,
  enableTagOverride: Boolean,
  meta: Map[String, String]
)

object ServiceResponse {
  implicit def ServiceResponseDecoder: Decoder[ServiceResponse] = new Decoder[ServiceResponse] {
    final def apply(j: HCursor): Decoder.Result[ServiceResponse] = {
      for {
        id <- j.downField("ID").as[String]
        address <- j.downField("Address").as[String]
        enableTagOverride <- j.downField("EnableTagOverride").as[Boolean]
        port <- j.downField("Port").as[Int]
        service <- j.downField("Service").as[String]
        tags <- j.downField("Tags").as[List[String]]
        meta <- j.downField("Meta").as[Option[Map[String, String]]]
      } yield ServiceResponse(
        service,
        id,
        tags,
        address,
        port,
        enableTagOverride,
        meta.getOrElse(Map.empty[String, String])
      )
    }
  }
}
