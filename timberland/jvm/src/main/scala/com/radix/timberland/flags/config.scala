package com.radix.timberland.flags

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs

case object config {
  /**
   * Config entries can be defined here, organized by flag name
   */
  val flagConfigParams: Map[String, List[FlagConfigEntry]] = Map(
    "minio" -> List(
      FlagConfigEntry(
        key = "aws_access_key_id",
        isSensitive = true,
        prompt = "Minio AWS Access Key ID",
        optional = true
      ),
      FlagConfigEntry(
        key = "aws_secret_access_key",
        isSensitive = true,
        prompt = "Minio AWS Secret Access Key",
        optional = true
      )
    ),
    "elemental" -> List(
      FlagConfigEntry(
        key = "username",
        isSensitive = true,
        prompt = "Elemental Machines Username",
      ),
      FlagConfigEntry(
        key = "password",
        isSensitive = true,
        prompt = "Elemental Machines Password",
      )
    )
  )

  // Defines functions that get run for
  val flagConfigHooks: Map[String, List[(Map[String, Option[String]], ServiceAddrs) => IO[Unit]]] = Map()

  // The list of flags that are enabled by default
  val flagDefaults: List[String] = List("core", "dev")
}
