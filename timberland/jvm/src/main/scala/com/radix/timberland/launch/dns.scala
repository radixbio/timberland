package com.radix.timberland.launch

import cats.effect.{IO, Resource}
import cats.implicits._
import com.radix.timberland.util.Util
import com.radix.timberland.util.Util.RootShell

object dns {
  private val dnsmasqResolverForConsul = os.root / "etc" / "dnsmasq.d" / "10-consul"
  private val resolvConf               = os.root / "etc" / "resolv.conf"
  private val systemdConf              = os.root / "etc" / "systemd" / "radix-consul.conf"
  private val cacheDir                 = os.home / ".radix-timberland" / "cache"

  def upDnsmasq(bindIP: String, interface: String): IO[Unit] = {
    // save existing config if it exists (unlikely)
    val dnsmasqCfg = {
      if (os.exists(dnsmasqResolverForConsul)) {
        RootShell.resource.use(_.move(dnsmasqResolverForConsul, cacheDir / "dns" / dnsmasqResolverForConsul.last))
      } else { IO.pure(()) }
    } *>
      RootShell.resource
        .use(shell => {
          for {
            _ <- shell.overwrite(
              s"""
                 |server=$bindIP#8600
                 |bind-interfaces
                 |interface=$interface
                 |""".stripMargin,
              cacheDir / "dns" / (dnsmasqResolverForConsul.last + ".radix"))
            _ <- shell.move(cacheDir / "dns" / "10-consul.radix", dnsmasqResolverForConsul)
            _ <- shell.exec(
            "systemctl restart dnsmasq")
          } yield ()
        })
    val resolvCfg = RootShell.resource.use(shell => {
      shell.copy(resolvConf, cacheDir / "dns" / (resolvConf.last + ".radix")) *> shell.overwrite(
        s"nameserver $bindIP",
        resolvConf) *> shell.read(cacheDir / "dns" / (resolvConf.last + ".radix")).flatMap(shell.append(_, resolvConf))
    })
    IO(os.makeDir.all(cacheDir / "dns")) *> dnsmasqCfg *> resolvCfg
  }

  def downDnsmasq(bindIP: String): IO[Unit] = {
    RootShell.resource.use(_.remove(dnsmasqResolverForConsul)) *> {
      if (os.exists(cacheDir / "dns" / dnsmasqResolverForConsul.last))
        RootShell.resource.use(
          shell =>
            shell.move(cacheDir / "dns" / dnsmasqResolverForConsul.last, dnsmasqResolverForConsul) *> shell.remove(
              cacheDir / "dns" / dnsmasqResolverForConsul.last))
      else IO.pure(())
    } *> RootShell.resource.use(_.move(cacheDir / "dns" / (resolvConf.last + ".radix"), resolvConf))
  }

  def saveIfExists(filename: os.Path, dir: os.Path): IO[Unit] = if (os.exists(filename)) {
    RootShell.resource.use(_.copy(filename, dir / filename.last))
  } else {
    IO.pure(())
  }

  def restoreIfExists(targetName: os.Path, cacheDir: os.Path): IO[Unit] =
    if (os.exists(cacheDir / targetName.last)) {
      RootShell.resource.use(_.move(cacheDir / targetName.last, targetName))
    } else {
      IO.pure(())
    }

  def upSystemd(bindIP: String): IO[Unit] = {
    saveIfExists(systemdConf, cacheDir / "dns") *>
    RootShell.resource.use { shell => for {
       _ <- shell.append(s"DNS=$bindIP\nDomains=~consul", systemdConf)
       _ <- shell.exec("iptables -t nat -A OUTPUT -d localhost -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
       _ <- shell.exec("iptables -t nat -A OUTPUT -d localhost -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
      } yield ()
    }
  }

  def downSystemd(bindIP: String): IO[Unit] = {
    RootShell.resource.use { shell => for {
        _ <- shell.exec("iptables -t nat -D OUTPUT -d localhost -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
        _ <- shell.exec("iptables -t nat -D OUTPUT -d localhost -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
        _ <- shell.remove(systemdConf)
      } yield ()
    } *> restoreIfExists(systemdConf, cacheDir / "dns")
  }

  def upIptables(bindIP: String): IO[Unit] = RootShell.resource.use(shell => for {
    _ <- shell.exec("iptables -t nat -A PREROUTING -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -A PREROUTING -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -A OUTPUT -d localhost -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -A OUTPUT -d localhost -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
  } yield ())

  def downIptables(bindIP: String): IO[Unit] = RootShell.resource.use(shell => for {
    _ <- shell.exec("iptables -t nat -D PREROUTING -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -D PREROUTING -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -D OUTPUT -d localhost -p udp -m udp --dport 53 -j REDIRECT --to-ports 8600")
    _ <- shell.exec("iptables -t nat -D OUTPUT -d localhost -p tcp -m tcp --dport 53 -j REDIRECT --to-ports 8600")
  } yield ())

  def identifyDNS: String = {
    //val dnsProcesses = ("netstat -tulpn" #| "grep :53\\s").!!
    //                   .split("\n").map(_.split("\\s+").last).map(_.split("/", 2).last)
    val netstat      = os.proc("/usr/bin/sudo", "netstat", "-tulpn").call()
    val grep         = os.proc("grep", ":53\\s").call(stdin = netstat.out.lines.mkString("\n"))
    val dnsProcesses = grep.out.trim.split("\n").map(_.split("\\s+").last).map(_.split("/", 2).last)
    // prioritize dnsmasq in the case that there are multiple services
    if (os.exists(os.root / "usr" / "sbin" / "dnsmasq")) {
      "dnsmasq"
    } else if (dnsProcesses.contains("systemd-resolve")) {
      "systemd"
      // use iptables in the case that no service is found
    } else {
      "iptables"
    }
  }

}
