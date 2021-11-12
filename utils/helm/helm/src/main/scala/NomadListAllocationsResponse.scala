package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._

final case class NomadListAllocationItem(id: String, jobId: String)

object NomadListAllocationItem {
  implicit def NomadListAllocationItemDecoder: Decoder[NomadListAllocationItem] = new Decoder[NomadListAllocationItem] {
    override def apply(j: HCursor): Result[NomadListAllocationItem] =
      for {
        id <- j.downField("ID").as[String]
        jobId <- j.downField("JobID").as[String]
      } yield NomadListAllocationItem(id, jobId)
  }
}

final case class NomadListAllocationsResponse(allocations: Vector[NomadListAllocationItem])

object NomadListAllocationsResponse {
  implicit def NomadListAllocationsResponseDecoder: Decoder[NomadListAllocationsResponse] =
    new Decoder[NomadListAllocationsResponse] {
      override def apply(j: HCursor): Result[NomadListAllocationsResponse] = {
        j.as[Vector[NomadListAllocationItem]].map(NomadListAllocationsResponse(_))
      }
    }
}
