package com.radix.utils.helm

import java.util.UUID
import scala.concurrent.duration._
import io.circe._

import scala.concurrent.duration.{Duration, FiniteDuration}

final case class SessionResponse(
  id: UUID,
  name: String,
  node: String,
  checks: List[String],
  lockDelay: FiniteDuration,
  behavior: String,
  ttl: Option[Duration],
  createIndex: Long,
  modifyIndex: Long,
)

object SessionResponse {
  implicit def SessionResponseDecoder: Decoder[SessionResponse] = new Decoder[SessionResponse] {
    final def apply(j: HCursor): Decoder.Result[SessionResponse] = {
      for {
        id <- j.downField("ID").as[UUID]
        name <- j.downField("Name").as[String]
        node <- j.downField("Node").as[String]
        checks <- j.downField("Checks").as[List[String]]
        lockDelay <- j.downField("LockDelay").as[Long]
        behavior <- j.downField("Behavior").as[String]
        ttl <- j.downField("TTL").as[String]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
      } yield SessionResponse(
        id,
        name,
        node,
        checks,
        lockDelay.seconds,
        behavior,
        if (ttl == "") None else Some(Duration(ttl)),
        createIndex,
        modifyIndex,
      )
    }
  }
}
