package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Yugabyte(dev: Boolean, quorumSize: Int) extends Job {
  val name = "yugabyte"
  val datacenters: List[String] = List("dc1")

  object YugabyteDaemonUpdate extends Update {
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
  override val update = YugabyteDaemonUpdate.some

  val groups = List(YugabyteGroup)

  object YugabyteGroup extends Group {
    val name = "yugabyte"
    val count = quorumSize
    val tasks = List(YBMaster, YBTServer) //TODO, YBTServer

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object YBMaster extends Task {
      val name = "ybmaster"
      val env = None
      object config extends Config {
        val image = "yugabytedb/yugabyte:latest"
        //        val command = "/home/yugabyte/bin/yb-master".some
        val command = "-jar".some
        val port_map = Map("ybmasteradmin" -> 7000, "ybmaster_rpc" -> 7100)
        //        val volumes = List().some
        val hostname = "ybmaster-${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = List("java").some
        val volumes = List(
          "/opt/radix/timberland:/timberland"
        ).some
        val args = dev match {
          case true =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "yugabyte-master",
              "--dev").some
          case false =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "yugabyte-master").some
        }
      }

      object YBMasterAdminUI extends Service {
        val tags = List("ybmaster", "admin")
        val port = "ybmasteradmin".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ybmasteradmin"
        }
      }

      //      object YBMasterRPC extends Service {
      //        val tags = List("ybmaster", "rpc")
      //        val port = "ybmaster_rpc".some
      //        val checks = List(check)
      //        object check extends Check {
      //          val `type` = "tcp"
      //          val port = "ybmaster_rpc"
      //        }
      //      }

      val services = List(YBMasterAdminUI) //NOTE: YBMaster RPC is bound to internal IP address, is not an external service
      val templates = None
      object resources extends Resources {
        val cpu = 250
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("ybmasteradmin" -> 7000.some, "ybmaster_rpc" -> 7100.some)
        }
      }
    }

    object YBTServer extends Task {
      val name = "ybtserver"
      val env = None
      object config extends Config {
        val image = "yugabytedb/yugabyte:latest"
        //        val command = "/home/yugabyte/bin/yb-tserver".some
        val command = "-jar".some
        val port_map = Map("ybtserver" -> 9000,
          "ysql" -> 5433,
          "ycql" -> 9042,
          "yedis" -> 6379,
          "ysqladmin" -> 13000) //TODO
        val volumes = List(
          "/opt/radix/timberland:/timberland"
        ).some
        val hostname = "ybtserver-${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = List("java").some
        val args = dev match {
          case true =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "yugabyte-tserver",
              "--dev").some
          case false =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "yugabyte-tserver").some
        }

        //val args = List("--flagfile", "/local/yugabyte/tserver.conf").some
        // The following args work with the correct weave IP and are what is reflected in the template
        // val args = List("--fs_data_dirs=/mnt/disk0,/mnt/disk1", "--start_pgsql_proxy", s"--tserver_master_addrs=yugabyte-yugabyte-ybmaster.service.consul:7100", "--rpc_bind_addresses=10.0.0.46").some
      }

      object YBTServer extends Service {
        val tags = List("ybtserver", "tserver")
        val port = "ybtserver".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ybtserver"
        }
      }

      object ysql extends Service {
        val tags = List("ybtserver", "ysql")
        val port = "ysql".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ysql"
        }
      }

      object ysqladmin extends Service {
        val tags = List("ybtserver", "ysqladmin")
        val port = "ysqladmin".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ysqladmin"
        }
      }

      object ycql extends Service {
        val tags = List("ybtserver", "ycql")
        val port = "ycql".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ycql"
        }
      }

      object yedis extends Service {
        val tags = List("ybtserver", "yedis")
        val port = "yedis".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "yedis"
        }
      }

      val services = List(YBTServer, ysql, ycql, yedis, ysqladmin)

      val templates = None
      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("ybtserver" -> 9001.some,
            "ysqladmin" -> 13000.some,
            "ysql" -> 5433.some,
            "ycql" -> 9042.some,
            "yedis" -> 6379.some)
        }
      }
    }
  }
  def jobshim(): JobShim = JobShim(name, Yugabyte(dev, quorumSize).assemble)
}
