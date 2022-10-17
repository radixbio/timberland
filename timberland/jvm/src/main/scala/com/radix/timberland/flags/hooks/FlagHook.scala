package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs

trait FlagHook {
  def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit]
}
