package com.radix.timberland.test.integration


class TimberlandIntegrationSpec extends TimberlandIntegration {
  override lazy val featureFlags = Map(
    "dev" -> true,
    "core" -> true,
    "yugabyte" -> true,
    "vault" -> true,
    "elemental" -> false,
    "elasticsearch" -> true
  )
}
