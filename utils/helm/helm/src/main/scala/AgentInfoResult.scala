package com.radix.utils.helm

import io.circe._
import io.circe.syntax._

final case class AgentInfoResult(
  datacenter: String,
  nodeName: String,
  nodeID: String,
  server: Boolean,
  rev: String,
  version: String,
)

object AgentInfoResult {
  implicit def AgentInfoDecoder: Decoder[AgentInfoResult] = (j: HCursor) => {
    val cfg = j.downField("Config")
    for {
      datacenter <- cfg.downField("Datacenter").as[String]
      nodeName <- cfg.downField("NodeName").as[String]
      nodeID <- cfg.downField("NodeID").as[String]
      server <- cfg.downField("Server").as[Boolean]
      rev <- cfg.downField("Revision").as[String]
      version <- cfg.downField("Version").as[String]
    } yield AgentInfoResult(datacenter, nodeName, nodeID, server, rev, version)
  }
}
