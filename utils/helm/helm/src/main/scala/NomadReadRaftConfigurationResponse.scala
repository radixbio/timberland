package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

final case class NomadReadRaftConfigurationResponse(index: Int, servers: List[NomadServerSummary])

object NomadReadRaftConfigurationResponse {
  implicit def NomadReadRaftConfigurationResponseDecoder: Decoder[NomadReadRaftConfigurationResponse] =
    new Decoder[NomadReadRaftConfigurationResponse] {
      override def apply(j: HCursor): Result[NomadReadRaftConfigurationResponse] =
        for {
          index <- j.downField("Index").as[Int]
          servers <- j.downField("Servers").as[List[NomadServerSummary]]
        } yield NomadReadRaftConfigurationResponse(index, servers)
    }
}

final case class NomadServerSummary(
  address: String,
  id: String,
  leader: Boolean,
  node: String,
  raftProtocol: String,
  voter: Boolean,
)

object NomadServerSummary {
  implicit def NomadServerSummaryDecoder: Decoder[NomadServerSummary] = new Decoder[NomadServerSummary] {
    override def apply(j: HCursor): Result[NomadServerSummary] =
      for {
        address <- j.downField("Address").as[String]
        id <- j.downField("ID").as[String]
        leader <- j.downField("Leader").as[Boolean]
        node <- j.downField("Node").as[String]
        raftProtocol <- j.downField("RaftProtocol").as[String]
        voter <- j.downField("Voter").as[Boolean]
      } yield NomadServerSummary(address, id, leader, node, raftProtocol, voter)
  }
}
