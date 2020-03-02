package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class ZookeeperDaemons(dev: Boolean, quorumSize: Int) extends Job {
  val name = "zookeeper-daemons"
  val datacenters: List[String] = List("dc1")
  val constraints = Some(List(kernelConstraint))
  val groups = List(zookeeper)

  object zookeeperUpdate extends Update {
    val stagger = "10s"
    val max_parallel = 1
    val min_healthy_time = "10s"
  }

  override val update = zookeeperUpdate.some

  object kernelConstraint extends Constraint {
    val operator = None
    val attribute = "${attr.kernel.name}".some
    val value = "linux"
  }

  object zookeeper extends Group {
    val name = "zookeeper"
    val count = quorumSize
    val constraints = List(distinctHost).some
    val tasks = List(zookeeper)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    object zookeeper extends Task {
      val name = "zookeeper"
      val env = None

      object zookeeperTemplate extends Template {
        override val source =
          "/opt/radix/timberland/nomad/zookeeper/zoo.tpl".some
        val destination = "local/conf/zoo_servers"
      }

      val templates = List(zookeeperTemplate).some

      object config extends Config {
        val image = "zookeeper:3.4"
        val hostname = "${attr.unique.hostname}-zookeeper"
        // CMD and ARGS here should invoke timberland
        //TODO grab timberland from artifactory, not volume mount
        val entrypoint = List("java").some
        val command = "-jar".some
        var args = dev match {
          case true =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "zookeeper",
              "--dev").some
          case false =>
            List("/timberland/exec/timberland-launcher_deploy.jar",
              "launch",
              "zookeeper").some
        }
        val port_map =
          Map("client" -> 2181, "follower" -> 2888, "othersrvs" -> 3888)
        //                dns_servers = ["${attr.unique.network.ip-address}"]
        //TODO swap mount point for artifactory
        val volumes = List(
          "/opt/radix/timberland:/timberland"
        ).some
      }
      object resources extends Resources {
        val cpu = 1000
        val memory = 2048

        object network extends Network {
          val networkPorts =
            Map("client" -> 2181.some,
              "follower" -> 2888.some,
              "othersrvs" -> 3888.some)

        }

      }

      object zookeeperClient extends Service {
        val tags = List("zookeeper-quorum", "zookeeper-client")
        val port = "client".some
        val checks = List(check)

        object check extends Check {
          val `type` = "tcp"
          val port = "client"
        }

      }

      object zookeeperFollower extends Service {
        val tags = List("zookeeper-quorum", "zookeeper-follower")
        val port = "follower".some
        val checks = List(check)

        object check extends Check {
          val `type` = "tcp"
          val port = "client"
        }

      }

      object zookeeperOthersrvs extends Service {
        val tags = List("zookeeper-quorum", "zookeeper-othersrvs")
        val port = "othersrvs".some
        val checks = List(check)

        object check extends Check {
          val `type` = "tcp"
          val port = "client"
        }

      }

      val services =
        List(zookeeperClient, zookeeperFollower, zookeeperOthersrvs)

    }

  }

  def jobshim(): JobShim =
    JobShim(name, ZookeeperDaemons(dev, quorumSize).assemble)
}