package com.radix.timberland.test.integration


class TimberlandIntegrationSpec extends TimberlandIntegration {
  override val yugabyte = true
  override val vault = true
  override val retool = true
  override val elk = true
}