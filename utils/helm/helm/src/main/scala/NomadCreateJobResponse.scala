package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._

/** Case class representing the result of create job API call to Nomad */
final case class NomadCreateJobResponse(
  evalId: String,
  evalCreateIndex: Long,
  jobModifyIndex: Long,
  warnings: String,
  index: Long,
  lastContact: Long,
  knownLeader: Boolean,
)

object NomadCreateJobResponse {
  implicit def nomadCreateJobResponseDecoder: Decoder[NomadCreateJobResponse] = new Decoder[NomadCreateJobResponse] {
    override def apply(j: HCursor): Result[NomadCreateJobResponse] =
      for {
        evalId <- j.downField("EvalID").as[String]
        evalCreateIndex <- j.downField("EvalCreateIndex").as[Long]
        jobModifyIndex <- j.downField("JobModifyIndex").as[Long]
        warnings <- j.downField("Warnings").as[String]
        index <- j.downField("Index").as[Long]
        lastContact <- j.downField("LastContact").as[Long]
        knownLeader <- j.downField("KnownLeader").as[Boolean]
      } yield NomadCreateJobResponse(
        evalId,
        evalCreateIndex,
        jobModifyIndex,
        warnings,
        index,
        lastContact,
        knownLeader,
      )
  }
}
