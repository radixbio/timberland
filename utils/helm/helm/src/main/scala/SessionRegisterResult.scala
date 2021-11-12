package com.radix.utils.helm

import io.circe._

import java.util.UUID

final case class SessionRegisterResult(id: UUID)
object SessionRegisterResult {
  implicit def SessionRegisterResultDecoder: Decoder[SessionRegisterResult] =
    new Decoder[SessionRegisterResult] {
      final def apply(j: HCursor): Decoder.Result[SessionRegisterResult] = {
        for {
          uuid <- j.downField("ID").as[UUID]
        } yield SessionRegisterResult(uuid)
      }
    }
}
