package com.radix.timberland

package object radixdefs {

  case class ServiceAddrs(
    consulAddr: String = "consul.service.consul",
    nomadAddr: String = "nomad.service.consul",
    vaultAddr: String = "vault.service.consul",
  )

  case class ACLTokens(
    masterToken: String,
    actorToken: String,
  )
}
