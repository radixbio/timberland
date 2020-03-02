package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class ElementalMachines(dev: Boolean, quorumSize: Int, vault_token: String)
  extends Job {
  val name = "elemental-machines"
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

  val groups = List(EMGroup)

  object EMGroup extends Group {
    val name = "em"
    val count = quorumSize
    val tasks = List(EMTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object EMTask extends Task {
      val name = "em"
      val env = Map(
        "VAULT_TOKEN" -> vault_token,
        "TCP_AKKA_PORT" -> "64501",
        "KAFKA_BOOTSTRAP_SERVERS" -> "OUTSIDE://kafka-daemons-kafka-kafka.service.consul:9092",
        "AVRO_SCHEMA_REGISTRY_URL" -> "http://kafka-companion-daemons-kafkacompanions-schemaregistry.service.consul:8081"
      ).some
      object config extends Config {
        val image =
          "registry.gitlab.com/radix-labs/monorepo/elemental-machines-sensors:latest"
        val command = None
        val port_map = Map("akka_port" -> 64501)
        val volumes = List("/opt/radix/timberland/nomad/elemental:/opt/conf").some
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = None
      }
      object AkkaListen extends Service {
        val tags = List("em", "akka-port")
        val port = "akka_port".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "akka_port"
        }
      }

      val services = List(AkkaListen)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("akka_port" -> 64501.some)
        }
      }
    }
  }
  def jobshim() =
    JobShim(name, ElementalMachines(dev, quorumSize, vault_token).assemble)
}