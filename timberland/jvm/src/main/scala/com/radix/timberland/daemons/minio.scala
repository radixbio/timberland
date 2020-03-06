package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Minio(dev: Boolean, quorumSize: Int) extends Job {
  val name = "minio"
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

  val groups = List(MinioGroup)

  object MinioGroup extends Group {
    val name = "minio"
    val count = 1
    val tasks = List(MinioTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object MinioTask extends Task {
      val name = "minio"
      val env = None
      object config extends Config {
        val image =
          "minio/minio:latest"
        val command = "server".some
        val port_map = Map("minio" -> 9000)
        val volumes = List("/opt/miniodata:/data").some
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = List("/data").some
      }
      object MinioService extends Service {
        val tags = List("minio", "client")
        val port = "minio".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "minio"
        }
      }

      val services = List(MinioService)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("minio" -> 10000.some)
        }
      }
    }
  }
  def jobshim() = JobShim(name, Minio(dev, quorumSize).assemble)
}