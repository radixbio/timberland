package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._

final case class NomadStopJobResponse(EvalID: String, EvalCreateIndex: Int, JobModifyIndex: Int)

object NomadStopJobResponse {
  implicit def NomadStopJobResponseDecoder: Decoder[NomadStopJobResponse] = new Decoder[NomadStopJobResponse] {
    override def apply(j: HCursor): Result[NomadStopJobResponse] =
      for {
        evalID <- j.downField("EvalID").as[String]
        evalCreateIndex <- j.downField("EvalCreateIndex").as[Int]
        jobModifyIndex <- j.downField("JobModifyIndex").as[Int]
      } yield NomadStopJobResponse(evalID, evalCreateIndex, jobModifyIndex)
  }
}
