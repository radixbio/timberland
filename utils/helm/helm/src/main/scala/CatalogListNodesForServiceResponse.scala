package com.radix.utils.helm

import io.circe._

final case class CatalogListNodesForServiceResponse(
  id: String,
  node: String,
  address: String,
  datacenter: String,
  taggedAddresses: TaggedAddresses,
  nodeMeta: Map[String, String],
  serviceKind: String,
  serviceID: String,
  serviceName: String,
  serviceTags: List[String],
  serviceAddress: String,
  serviceMeta: Map[String, String],
  servicePort: Int,
  serviceEnableTagOverride: Boolean,
//                                                    serviceProxyDestination: String,
  createIndex: Long,
  modifyIndex: Long,
)
object CatalogListNodesForServiceResponse {
  implicit def catalogListNodesForService: Decoder[CatalogListNodesForServiceResponse] =
    new Decoder[CatalogListNodesForServiceResponse] {
      final def apply(j: HCursor): Decoder.Result[CatalogListNodesForServiceResponse] = {
        for {
          id <- j.downField("ID").as[String]
          node <- j.downField("Node").as[String]
          addr <- j.downField("Address").as[String]
          dc <- j.downField("Datacenter").as[String]
          tagAddr <- j.downField("TaggedAddresses").as[TaggedAddresses]
          nodeMeta <- j.downField("NodeMeta").as[Map[String, String]]
          serviceKind <- j.downField("ServiceKind").as[String]
          serviceID <- j.downField("ServiceID").as[String]
          serviceName <- j.downField("ServiceName").as[String]
          serviceTags <- j.downField("ServiceTags").as[List[String]]
          serviceAddress <- j.downField("ServiceAddress").as[String]
          serviceMeta <- j.downField("ServiceMeta").as[Map[String, String]]
          servicePort <- j.downField("ServicePort").as[Int]
          serviceEnableTagOverride <- j.downField("ServiceEnableTagOverride").as[Boolean]
          //          serviceProxyDestination  <- j.downField("ServiceProxyDestination").as[String]
          createIndex <- j.downField("CreateIndex").as[Long]
          modifyIndex <- j.downField("ModifyIndex").as[Long]
        } yield CatalogListNodesForServiceResponse(
          id,
          node,
          addr,
          dc,
          tagAddr,
          nodeMeta,
          serviceKind,
          serviceID,
          serviceName,
          serviceTags,
          serviceAddress,
          serviceMeta,
          servicePort,
          serviceEnableTagOverride,
          //            serviceProxyDestination,
          createIndex,
          modifyIndex,
        )
      }
    }
}
