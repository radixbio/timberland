package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Apprise(dev: Boolean, quorumSize: Int) extends Job {
  val name = "apprise"
  val datacenters: List[String] = List("dc1")

  object DaemonUpdate extends Update {
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
  override val update = DaemonUpdate.some

  val groups = List(AppriseGroup)

  object AppriseGroup extends Group {
    val name = "apprise"
    val count = 1
    val tasks = List(AppriseTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object AppriseTask extends Task {
      val name = "apprise"
      val env = None
      object config extends Config {
        val image =
          "caronc/apprise:latest"
        val command = None
        val port_map = Map("apprise" -> 8000)
        val volumes = None
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = None
      }
      object AppriseService extends Service {
        val tags = List("apprise", "listen")
        val port = "apprise".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "apprise"
        }
      }

      val services = List(AppriseService)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("apprise" -> 10001.some)
        }
      }
    }
  }
  def jobshim() = JobShim(name, Apprise(dev, quorumSize).assemble)
}