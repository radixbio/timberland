package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens

trait FlagHook {
  val possibleOptions: Set[String] = Set.empty
  def run(options: Map[String, String], addrs: ServiceAddrs, tokens: AuthTokens): IO[Unit]
}
