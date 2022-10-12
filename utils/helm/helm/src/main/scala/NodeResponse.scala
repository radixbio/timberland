package com.radix.utils.helm

import io.circe._

/** Case class representing a health check as returned from an API call to Consul */
final case class NodeResponse(
  id: String,
  node: String,
  address: String,
  datacenter: String,
  meta: Map[String, String],
  taggedAddresses: TaggedAddresses,
  createIndex: Long,
  modifyIndex: Long,
)

object NodeResponse {
  implicit def NodeResponseDecoder: Decoder[NodeResponse] = new Decoder[NodeResponse] {
    final def apply(j: HCursor): Decoder.Result[NodeResponse] = {
      for {
        id <- j.downField("ID").as[String]
        node <- j.downField("Node").as[String]
        address <- j.downField("Address").as[String]
        datacenter <- j.downField("Datacenter").as[String]
        meta <- j.downField("Meta").as[Map[String, String]]
        taggedAddresses <- j.downField("TaggedAddresses").as[TaggedAddresses]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
      } yield NodeResponse(id, node, address, datacenter, meta, taggedAddresses, createIndex, modifyIndex)
    }
  }
}

final case class TaggedAddresses(lan: String, wan: String)

object TaggedAddresses {
  implicit def TaggedAddressesDecoder: Decoder[TaggedAddresses] = new Decoder[TaggedAddresses] {
    final def apply(j: HCursor): Decoder.Result[TaggedAddresses] = {
      for {
        lan <- j.downField("lan").as[String]
        wan <- j.downField("wan").as[String]
      } yield TaggedAddresses(lan, wan)
    }
  }
}
