package com.radix.timberland.launch

import java.nio.file.Path

import cats.effect.{IO, Resource}
import cats.implicits._
import oshi.software.os.linux.LinuxOperatingSystem

object dns {
  trait DNS_method {
    def up(): IO[Unit]
    def down(): IO[Unit]
  }

  case class Netplan() extends DNS_method {
    private val netplan_file = os.root / "etc" / "netplan" / "02-radix_runtime_dns.yaml"
    private val netplan_config = """network:
                                   |  version: 2
                                   |  ethernets:
                                   |    eth0:
                                   |      dhcp4-overrides:
                                   |        use-dns: false
                                   |      nameservers:
                                   |        addresses:
                                   |          - 127.0.0.1
                                   |""".stripMargin

    def up(): IO[Unit] = {
      os.write.over(netplan_file, netplan_config)
      val result = os.proc(Seq("/usr/bin/sudo", "netplan", "apply")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo netplan apply' returned error code $result.exitCode")
      }
      IO.unit
    }
    def down(): IO[Unit] = {
      os.remove(netplan_file)
      val result = os.proc(Seq("/usr/bin/sudo", "netplan", "apply")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo netplan apply' returned error code $result.exitCode")
      }
      IO.unit
    }
  }
  case class Ifcfg() extends DNS_method {
    private val ifcfg_file = os.root / "etc" / "sysconfig" / "network-scripts" / "ifcfg-eth0"
    private val ifcfg_line = """
                               |DNS1=127.0.0.1
                               |""".stripMargin

    def up(): IO[Unit] = {
      val config_text = os.read(ifcfg_file)
      if (!config_text.contains(ifcfg_line)) {
        os.write.append(ifcfg_file, ifcfg_line)
        val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "network")).call()
        if (result.exitCode != 0) {
          throw new RuntimeException(s"'sudo systemctl restart network' returned error code $result.exitCode")
        }
      }
      IO.unit
    }
    def down(): IO[Unit] = {
      val config_text = os.read(ifcfg_file)
      os.write.over(ifcfg_file, config_text.replaceAll(ifcfg_line, ""))
      val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "network")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo systemctl restart network' returned error code $result.exitCode")
      }
      IO.unit
    }
  }

  case class Dhclient() extends DNS_method {
    private val dhclient_file = os.root / "etc" / "dhcp" / "dhclient.conf"
    private val dhclient_line = "prepend domain-name-servers 127.0.0.1;"

    def up(): IO[Unit] = {
      val config_text = os.read(dhclient_file)
      if (!(config_text.contains(dhclient_line))) {
        os.write.over(dhclient_file, dhclient_line + "\n" + config_text)
      }
      val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "network")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo systemctl restart network' returned error code $result.exitCode")
      }
      IO.unit
    }
    def down(): IO[Unit] = {
      val config_text = os.read(dhclient_file)
      os.write.over(dhclient_file, config_text.replaceAll(dhclient_line, ""))
      val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "network")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo systemctl restart network' returned error code $result.exitCode")
      }
      IO.unit
    }
  }

  case class Resolved() extends DNS_method {
    private val resolved_file = os.root / "etc" / "systemd" / "resolved.conf" // Different from /etc/resolv.conf !!
    private val resolved_line = "DNS=127.0.0.1" // Also requires "[Resolve]" to establish the config section

    private val dns_key = "DNS"
    private val dns_val = "127.0.0.1"
    private val dom_key = "Domains"
    private val dom_val = "~consul"

    private def add_key(k: String, v: String)(lines: List[String]): List[String] = {
      def _add(line: String): String = {
        if (line.startsWith(k + "=") & (!line.contains(v))) {
          if (line.endsWith("=")) {
            line + v
          } else {
            line + " " + v
          }
        } else {
          line
        }
      }

      if (lines.exists(l => l.startsWith(k))) {
        lines.map(_add)
      } else {
        lines :+ s"$k=$v"
      }
    }

    private def rem_key(k: String, v: String)(lines: List[String]): List[String] = {
      def _rem(line: String): String = {
        if (line.startsWith(k + "=") & line.contains(v)) {
          line.replaceAll(v, "")
        } else {
          line
        }
      }

      if (lines.exists(l => l.startsWith(k))) {
        lines.map(_rem)
      } else {
        lines
      }
    }

    def up(): IO[Unit] = {
      val config_lines = os.read(resolved_file).split("\n").toList
      val add_keys = (add_key(dns_key, dns_val) _) compose (add_key(dom_key, dom_val) _)

      // TODO check for "[Resolved]" and add if missing.
      os.write.over(resolved_file, add_keys(config_lines).mkString("\n") + "\n")
      val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "systemd-resolved")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo systemctl restart systemd-resolved' returned error code $result.exitCode")
      }
      IO.unit
    }

    def down(): IO[Unit] = {
      val config_text = os.read(resolved_file).split("\n").toList
      val rm_keys = (rem_key(dns_key, dns_val) _) compose (rem_key(dom_key, dom_val) _)

      os.write.over(resolved_file, rm_keys(config_text).mkString("\n") + "\n")
      val result = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "systemd-resolved")).call()
      if (result.exitCode != 0) {
        throw new RuntimeException(s"'sudo systemctl restart systemd-resolved' returned error code $result.exitCode")
      }
      IO.unit
    }
  }

  // Retained for completeness for now.
  case class Resolvconf() extends DNS_method {
    private val resolvconf_file = os.root / "etc" / "resolv.conf"
    private val resolvconf_line = "nameserver 127.0.0.1"

    def up(): IO[Unit] = {
      val conf_text = os.read(resolvconf_file)
      // We expect that iff our nameserver is present, /etc/resolv.conf is write-protected.
      if (!(conf_text.contains(resolvconf_line))) {
        val conf_lines = conf_text.split("\n").toList
        val first_nameserver_line = conf_lines.indexWhere(_.contains("nameserver"))
        // There may also be other nameservers already set, in which case we want ours to be first.
        val new_lines = {
          first_nameserver_line >= 0 match {
            case true  => conf_lines.patch(first_nameserver_line, List(resolvconf_line), 0)
            case false => conf_lines :+ resolvconf_line
          }
        }
        os.remove(resolvconf_file)
        os.write.over(resolvconf_file, new_lines.mkString("\n") + "\n")
        val result = os.proc(Seq("/usr/bin/sudo", "chattr", "+i", resolvconf_file.toString)).call()
        if (result.exitCode != 0) {
          scribe.warn("Could not set permissions /etc/resolv.conf")
        }
      }
      IO.unit
    }

    def down(): IO[Unit] = {
      val result = os.proc(Seq("/usr/bin/sudo", "chattr", "-i", resolvconf_file.toString)).call()
      if (result.exitCode != 0) {
        throw new RuntimeException("Could not unprotect /etc/resolv.conf")
      }

      val conf_text = os.read(resolvconf_file)
      os.write.over(resolvconf_file, conf_text.replaceAll(resolvconf_line, ""))

      IO.unit
    }
  }

  case class NetworkManager() extends DNS_method {
    def up(): IO[Unit] = {
      dns.Resolved().up()
      val ret = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "NetworkManager")).call()
      if (!(ret.exitCode == 0)) {
        println(s"NetworkManager config error codes: ${ret.exitCode}")
      }
      Thread.sleep(1000) // DNS will still fail for a second as NM comes back up
      IO.unit
    }
    def down(): IO[Unit] = {
      dns.Resolved().down()
      val ret = os.proc(Seq("/usr/bin/sudo", "systemctl", "restart", "NetworkManager")).call()
      if (!(ret.exitCode == 0)) {
        println(s"NetworkManager config error codes: ${ret.exitCode}")
      }
      IO.unit
    }
  }

  case class NoMethod() extends DNS_method {
    def up(): IO[Unit] = {
      scribe.error("No valid DNS method found! (up)")
      IO.unit
    }
    def down(): IO[Unit] = {
      scribe.error("No valid DNS method found! (down)")
      IO.unit
    }
  }

  private val NM_string = "Generated by NetworkManager"
  private val dhclient_string = "generated by /usr/sbin/dhclient-script"
  private val resolved_string = "This file is managed by man:systemd-resolved(8). Do not edit."
  def identify_DNS_control: DNS_method = {
    val resolvconf_text = os.read(os.root / "etc" / "resolv.conf")
    if (resolvconf_text.contains(NM_string)) {
      scribe.info("Detected NetworkManager DNS control.")
      NetworkManager()
    } else if (resolvconf_text.contains(dhclient_string)) {
      scribe.info("Detected dhclient DNS control.")
      Dhclient()
    } else if (resolvconf_text.contains(resolved_string)) {
      scribe.info("Detected systemd-resolve DNS control")
      Resolved()
    } else {
      scribe.info("Did not successfully detect DNS control!")
      NoMethod()
    }
  }

  def up(): IO[Unit] = {
    identify_DNS_control.up()
  }
  def down(): IO[Unit] = {
    identify_DNS_control.down()
  }
}
