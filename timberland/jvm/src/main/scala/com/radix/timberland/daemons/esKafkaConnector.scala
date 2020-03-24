package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class ESKafkaConnector() extends Job {
  val name = "es-kafka-connector"
  val datacenters: List[String] = List("dc1")

  override def `type`: Option[String] = "batch".some

  val constraints = None

  val groups = List(ESKafkaConnectorGroup)

  object ESKafkaConnectorGroup extends Group {
    val name = "connector"
    val count = 1
    val tasks = List(ConnectorTask)

    val constraints = None

    object ConnectorTask extends Task {
      val name = "elasticsearch-kafka-connector"
      val env = None
      object config extends Config {
        val image =
          "appropriate/curl"
        val command = "/local/start.sh".some
        val port_map = Map()
        val volumes = None
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = None
      }

      val services = List()

      object startTemplate extends Template {
        override val source =
          "/opt/radix/timberland/nomad/connect/start.sh".some
        val destination = "local/start.sh"
        override val perms = "755"
      }

      val templates = List(startTemplate).some

      object resources extends Resources {
        val cpu = 1000
        val memory = 100
        object network extends Network {
          val networkPorts =
            Map()
        }
      }
    }
  }
  def jobshim() = JobShim(name, ESKafkaConnector().assemble)
}