package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class VaultDaemon(dev: Boolean, quorumSize: Int) extends Job {
  val name = "vault-daemon"
  val datacenters: List[String] = List("dc1")

  object VaultDaemonUpdate extends Update {
    val stagger = "10s"
    val max_parallel = 1
    val min_healthy_time = "10s"
  }

  object kernelConstraint extends Constraint {
    val operator = None
    val attribute = "${attr.kernel.name}".some
    val value = "linux"
  }

  val constraints = List(kernelConstraint).some
  override val update = VaultDaemonUpdate.some

  val groups = List(VaultGroup)

  object VaultGroup extends Group {
    val name = "vault"
    val count = quorumSize
    val tasks = List(VaultTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object VaultTask extends Task {
      val name = "vault"
      val env = Map("" -> "").some
      //      val env = Map("VAULT_LOCAL_CONFIG" -> "\n                storage \"consul\" {\n                    address = \"127.0.0.1:8500\"\n                    path = \"vault\"\n                }\n                listener \"tcp\" {\n                    address = \"127.0.0.1:8200\"\n                \n                }\n                telemetry {\n                    statsite_address = \"127.0.0.1:8125\"\n                }\n                plugin-directory = \"/opt/plugins/\"\n                ").some
      object config extends Config {
        val image = "registry.gitlab.com/radix-labs/monorepo/vault:latest"
        val command = "vault".some
        val port_map = Map("vault_listen" -> 8200, "telemetry" -> 8125)
        override val cap_add = List("IPC_LOCK").some
        val volumes = None
        val hostname = "${attr.unique.hostname}-vault"
        val entrypoint = None
        val args = List("server", "-config", "/opt/vault_config.conf").some
        override val privileged = true.some
      }
      object VaultListen extends Service {
        val tags = List("vault", "vault-listen")
        val port = "vault_listen".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "vault_listen"
        }
      }

      val services = List(VaultListen)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("vault_listen" -> 8200.some, "telemetry" -> 8125.some)
        }
      }
    }
  }
  def jobshim() = JobShim(name, VaultDaemon(dev, quorumSize).assemble)
}