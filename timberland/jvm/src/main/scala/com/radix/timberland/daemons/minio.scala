package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Minio(dev: Boolean, quorumSize: Int, upstreamAccessKey: Option[String], upstreamSecretKey: Option[String]) extends Job {
  val name = "minio-job"
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
    val name = "minio-group"
    val count = 1
    val access_key = upstreamAccessKey match {
      case Some(ak) => ak
      case None => "minio-access-key"
    }
    val secret_key = upstreamSecretKey match {
      case Some(sk) => sk
      case None => "minio-secret-key"
    }

    val tasks = upstreamAccessKey match {
      case Some(_) => List(MinioTaskLocal, MinioTaskRemote, NginxMinioTask)
      case None => List(MinioTaskLocal, NginxMinioTask)
    }


    val constraints = None
    object NginxMinioTask extends Task {
      val name = "nginx-minio"
      val env = None

      object config extends Config {
        val image = "nginx:latest"
        val command = None
        val port_map = Map("nginx" -> 9000)
        val volumes = upstreamAccessKey match {
          case Some(_) => List("/opt/radix/timberland/nginx/nginx-minios.conf:/etc/nginx/nginx.conf").some
          case None => List("/opt/radix/timberland/nginx/nginx-noupstream.conf:/etc/nginx/nginx.conf").some
        }
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = None
      }

      object MinioNginxService extends Service {
        val tags = List("minio", "client")
        val port = "nginx".some
        val checks = List(check)
        override val address_mode = "host"
        object check extends Check {
          val `type` = "tcp"
          val port = "nginx"
        }

      }

      val services = List(MinioNginxService)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 300

        object network extends Network {
          val networkPorts =
            Map("nginx" -> 1339.some)
        }

      }

    }

    object MinioTaskLocal extends Task {
      val name = "minio-local"
      val env = Map("MINIO_ACCESS_KEY" -> access_key,
        "MINIO_SECRET_KEY" -> secret_key,
        "MINIO_NOTIFY_KAFKA_ENABLE" -> "true",
        "MINIO_NOTIFY_KAFKA_BROKERS" -> "kafka-daemons-kafka-kafka.service.consul:29092",
        "MINIO_NOTIFY_KAFKA_TOPIC" -> "bucketevents").some

      object config extends Config {
        val image = "minio/minio:latest"
        val command = None
        val port_map = Map("minio" -> 9000)
        val volumes = List("/opt/radix/minio_data:/data").some
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = List("minio", "--compat").some
        val args = List("server", "/data").some
      }
      object MinioLocalService extends Service {
        val tags = List("minio", "client")
        val port = "minio".some
        val checks = List(check)
//        override val address_mode = "host"
        object check extends Check {
          val `type` = "tcp"
          val port = "minio"
        }

      }
      val services = List(MinioLocalService)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 300

        object network extends Network {
          val networkPorts =
            Map("minio" -> 9000.some)
        }

      }

    }

    object MinioTaskRemote extends Task {
      val name = "minio-remote"
      val env = Map("MINIO_ACCESS_KEY" -> access_key, "MINIO_SECRET_KEY" -> secret_key).some

      object config extends Config {
        val image = "minio/minio:latest"
        val command = None
        val port_map = Map("minio_remote" -> 9000)
        val volumes = List("/opt/radix/minio_data_remote:/data").some
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = List("minio").some
        val args = List("gateway", "s3").some
        override val dns_servers = Some(List("169.254.1.1", "8.8.8.8", "1.1.1.1"))
      }
      object MinioRemoteService extends Service {
        val tags = List("minio", "client")
        val port = "minio_remote".some
        val checks = List(check)
//        override val address_mode = "host"
        object check extends Check {
          val `type` = "tcp"
          val port = "minio_remote"
        }

      }
      val services = List(MinioRemoteService)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 300

        object network extends Network {
          val networkPorts =
            Map("minio_remote" -> 9002.some)
        }

      }

    }


  }

  def jobshim() = JobShim(name, Minio(dev, quorumSize, upstreamAccessKey, upstreamSecretKey).assemble)
}
