package com.radix.utils.helm

import io.circe._
import io.circe.syntax._

sealed abstract class HealthStatus extends Product with Serializable

object HealthStatus {

  final case object Passing extends HealthStatus
  final case object Unknown extends HealthStatus
  final case object Warning extends HealthStatus
  final case object Critical extends HealthStatus

  def fromString(s: String): Option[HealthStatus] =
    s.toLowerCase match {
      case "passing"  => Some(Passing)
      case "warning"  => Some(Warning)
      case "critical" => Some(Critical)
      case "unknown"  => Some(Unknown)
      case _          => None
    }

  def toString(hs: HealthStatus): String =
    hs match {
      case Passing  => "passing"
      case Warning  => "warning"
      case Critical => "critical"
      case Unknown  => "unknown"
    }

  // TODO: This use of Left and Right is bogus, how to get the correct type aliases for Decoder.Result?
  implicit val HealthStatusDecoder: Decoder[HealthStatus] = new Decoder[HealthStatus] {
    final def apply(j: HCursor): Decoder.Result[HealthStatus] = {
      j.as[String].flatMap { s =>
        fromString(s) match {
          case Some(r) => Right(r)
          case None    => Left(DecodingFailure("invalid health status: $s", j.history))
        }
      }
    }
  }

  implicit val HealthStatusEncoder: Encoder[HealthStatus] = new Encoder[HealthStatus] {
    final def apply(hs: HealthStatus): Json = hs.toString.asJson
  }
}
