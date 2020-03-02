package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class KafkaDaemons(dev: Boolean, quorumSize: Int, servicePort: Int = 9092)
  extends Job {
  val name = "kafka-daemons"
  val datacenters: List[String] = List("dc1")
  val constraints = Some(List(kernelConstraint))
  val groups = List(kafka)

  object kafkaUpdate extends Update {
    val stagger = "10s"
    val max_parallel = 1
    val min_healthy_time = "10s"
  }

  override val update = kafkaUpdate.some

  object kernelConstraint extends Constraint {
    val operator = None
    val attribute = "${attr.kernel.name}".some
    val value = "linux"
  }

  object kafka extends Group {
    val name = "kafka"
    val count: Int = quorumSize
    val constraints = List(distinctHost).some
    val tasks = List(kafkaTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    object kafkaTask extends Task {
      val name = "kafka"
      val interBrokerPort = 29092
      var env: Option[Map[String, String]] = dev match {
        case true =>
          Map(
            "KAFKA_BROKER_ID" -> "${NOMAD_ALLOC_INDEX}",
            "TOPIC_AUTO_CREATE" -> "true",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1",
            "KAFKA_LISTENERS" -> "INSIDE://0.0.0.0:29092,OUTSIDE://0.0.0.0:${NOMAD_PORT_kafka}",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "INSIDE"
          ).some
        case false =>
          Map(
            "KAFKA_BROKER_ID" -> "${NOMAD_ALLOC_INDEX}",
            "TOPIC_AUTO_CREATE" -> "true",
            "KAFKA_LISTENERS" -> "INSIDE://0.0.0.0:29092,OUTSIDE://0.0.0.0:${NOMAD_PORT_kafka}",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "INSIDE"
          ).some
      }

      val services = List(kafkaPlaintext)

      object kafkaTemplate extends Template {
        override val source =
          "/opt/radix/timberland/nomad/zookeeper/zoo.tpl".some
        val destination = "local/conf/zoo_servers"
      }

      val templates = List(kafkaTemplate).some

      object config extends Config {
        val image = "confluentinc/cp-kafka:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka"
        val entrypoint = List("java").some
        val command = "-jar".some
        var args = dev match {
          case true =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "kafka",
              "--dev").some
          case false =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "kafka").some
        }
        val port_map = Map("kafka" -> servicePort)
        val volumes = List("/opt/radix/timberland:/timberland").some
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 2048

        object network extends Network {
          val networkPorts = Map("kafka" -> servicePort.some)
        }

        val templates = List(kafkaTemplate).some
      }

      object kafkaPlaintext extends Service {
        val tags = List("kafka-quorum", "kafka-plaintext")
        val port = "kafka".some
        val checks = List(check)

        object check extends Check {
          val `type` = "tcp"
          val port = "kafka"
        }

      }
    }

  }

  def jobshim(): JobShim =
    JobShim(name, KafkaDaemons(dev, quorumSize, servicePort).assemble)
}