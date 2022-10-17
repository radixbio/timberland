package com.radix.timberland.test.integration


class TimberlandIntegrationSpec extends TimberlandIntegration {
  override val featureFlags = Map(
    "dev" -> true,
    "core" -> true,
    "yugabyte" -> true,
    "vault" -> true,
    "retool" -> true,
    "elemental" -> false,
    "es" -> true
  )
}