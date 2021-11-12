package com.radix.utils.helm

import java.util.Base64
import io.circe._

/** Case class representing the response to a KV "Read Key" API call to Consul */
final case class KVGetResult(
  key: String,
  value: String,
  flags: Long,
  session: Option[String],
  lockIndex: Long,
  createIndex: Long,
  modifyIndex: Long
)

object KVGetResult {
  implicit def KVGetResultDecoder: Decoder[KVGetResult] = new Decoder[KVGetResult] {
    final def apply(j: HCursor): Decoder.Result[KVGetResult] = {
      for {
        key <- j.downField("Key").as[String]
        value <- j
          .downField("Value")
          .as[String]
          .map(in => new String(Base64.getDecoder.decode(in))) // Consul returns stuff in Base64
        flags <- j.downField("Flags").as[Long]
        session <- j.downField("Session").as[Option[String]]
        lockIndex <- j.downField("LockIndex").as[Long]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
      } yield KVGetResult(key, value, flags, session, lockIndex, createIndex, modifyIndex)
    }
  }
}
