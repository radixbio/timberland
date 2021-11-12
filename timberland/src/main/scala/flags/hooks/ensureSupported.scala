package com.radix.timberland.flags.hooks

import cats.effect.IO
import cats.implicits._
import com.radix.timberland.radixdefs.ServiceAddrs

object ensureSupported extends FlagHook {
  val osname = System.getProperty("os.name").toLowerCase match {
    case mac if mac.contains("mac")             => "darwin"
    case linux if linux.contains("linux")       => "linux"
    case windows if windows.contains("windows") => "windows"
  }

  val arch = System.getProperty("os.arch") match {
    case x86 if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains("x86") => x86
    case aarch64 if aarch64.contains("aarch64")                                      => aarch64
    case x @ _                                                                       => x
  }

  val jreVersion = System.getProperty("java.runtime.version")

  val checks: List[IO[Unit]] = List(
    // ensure JDK 11
    if (!jreVersion.startsWith("11.")) {
      IO.raiseError(new RuntimeException("JRE 11 is the only supported runtime environment."))
    } else {
      IO.unit
    },
    // ensure 64b
    if (!List("amd64", "x86", "aarch64").contains(arch)) {
      IO.raiseError(new RuntimeException("aarch64 and amd64 are the only supported CPU architectures."))
    } else {
      IO.unit
    },
    // ensure win10
    if (osname.contains("windows") && !System.getProperty("os.name").endsWith("10")) {
      IO.raiseError(new RuntimeException("Windows 10 is the only supported Windows version."))
    } else {
      IO.unit
    }
  )

  override def run(options: Map[String, Option[String]], addrs: ServiceAddrs): IO[Unit] = checks.sequence.map(_ => ())
}
