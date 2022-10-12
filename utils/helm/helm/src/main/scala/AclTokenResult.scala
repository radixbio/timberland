package com.radix.utils.helm

import io.circe._
import io.circe.syntax._

final case class AclTokenResult(
  accessorId: String,
  secretId: String,
  description: String,
)

object AclTokenResult {
  implicit def AclTokenDecoder: Decoder[AclTokenResult] = (j: HCursor) => {
    for {
      accessorId <- j.downField("AccessorID").as[String]
      secretId <- j.downField("SecretID").as[String]
      description <- j.downField("Description").as[String]
    } yield AclTokenResult(accessorId, secretId, description)
  }
}
