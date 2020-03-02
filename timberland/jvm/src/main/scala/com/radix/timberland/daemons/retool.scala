package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Retool(dev: Boolean, quorumSize: Int) extends Job {
  val name = "retool"
  val datacenters: List[String] = List("dc1")

  object RetoolDaemonUpdate extends Update {
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
  override val update = RetoolDaemonUpdate.some

  val groups = List(RetoolGroup)

  object RetoolGroup extends Group {
    val name = "retool"
    val count = quorumSize
    val tasks = List(Postgres,
      //DbConnector,
      //DbSshConnector,
      //RetoolJobsRunner,
      RetoolMain)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object RetoolMain extends Task {
      val name = "retool-main"
      val env = Map(
        "NODE_ENV" -> "production",
        //        "STAGE" -> "local",
        "COOKIE_INSECURE" -> "true",
        "RT_POSTGRES_DB" -> "retool",
        "RT_POSTGRES_USER" -> "retool_internal_user",
        "RT_POSTGRES_HOST" -> "retool-retool-postgres.service.consul",
        "RT_POSTGRES_PORT" -> "5432",
        "RT_POSTGRES_PASSWORD" -> "retool",
        "POSTGRES_DB" -> "retool",
        "POSTGRES_USER" -> "retool_internal_user",
        "POSTGRES_HOST" -> "retool-retool-postgres.service.consul",
        "POSTGRES_PORT" -> "5432",
        "POSTGRES_PASSWORD" -> "retool",
        //        "SERVICE_TYPE" -> "MAIN_BACKEND",
        //        "DB_CONNECTOR_HOST" -> "http://retool-retool-db-connector.service.consul",
        //        "DB_CONNECTOR_PORT" -> "3002",
        //        "DB_SSH_CONNECTOR_HOST" -> "http://retool-retool-db-ssh-connector.service.consul",
        //        "DB_SSH_CONNECTOR_PORT" -> "3003",
        "LICENSE_KEY" -> "6b3b3a6b-78a8-4805-bef4-07bedb0cfd08",
        "JWT_SECRET" -> ",IkZ`r;ti$z0V8'CRt$%Ur!zq_Cw0}t8",
        "ENCRYPTION_KEY" -> "0#V9=oZ<q?f*jZFJNmq779u-mCttbLb"
      ).some
      object config extends Config {
        val image = "tryretool/backend:latest"
        val command = "bash".some
        val port_map = Map("retool" -> 3000)
        //        override val cap_add = List("IPC_LOCK").some
        val volumes = List().some
        val hostname = "retool-${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = None
        val args = List(
          "-c",
          "./docker_scripts/wait-for-it.sh retool-retool-postgres.service.consul:5432; ./docker_scripts/start_api.sh").some
      }

      object RetoolService extends Service {
        val tags = List("retool", "retool-service")
        val port = "retool".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "retool"
        }
      }

      val services = List(RetoolService)
      val templates = List().some
      object resources extends Resources {
        val cpu = 1000
        val memory = 3000
        object network extends Network {
          val networkPorts = Map("retool" -> 3000.some)
        }
      }
    }

    object Postgres extends Task {
      val name = "postgres"
      val env = Map("POSTGRES_DB" -> "retool",
        "POSTGRES_USER" -> "retool_internal_user",
        "POSTGRES_HOST" -> "0.0.0.0",
        "POSTGRES_PORT" -> "5432",
        "POSTGRES_PASSWORD" -> "retool").some
      object config extends Config {
        val image = "postgres:9.6.5"
        val command = "postgres".some
        val port_map = Map("postgresdb" -> 5432)
        //        override val cap_add = List("IPC_LOCK").some
        val volumes = List("data:/var/lib/postgresql/data").some
        //        val volumes = List().some
        val hostname = "postgres-${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = None
        val args = List().some
      }

      object PostgresService extends Service {
        val tags = List("retool", "postgres")
        val port = "postgresdb".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "postgresdb"
        }
      }

      val services = List(PostgresService)
      val templates = List().some
      object resources extends Resources {
        val cpu = 250
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("postgresdb" -> 5432.some)
        }
      }
    }
  }
  def jobshim(): JobShim = JobShim(name, Retool(dev, quorumSize).assemble)
}