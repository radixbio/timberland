package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class Elasticsearch(dev: Boolean, quorumSize: Int) extends Job {
  val name = "elasticsearch"
  val datacenters: List[String] = List("dc1")

  object ElasticsearchDaemonUpdate extends Update {
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
  override val update = ElasticsearchDaemonUpdate.some

  val groups = List(ElasticsearchGroup, KibanaGroup)

  object ElasticsearchGroup extends Group {
    val name = "elasticsearch"
    val count = quorumSize
    val tasks = List(EsGenericNode)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object EsGenericNode extends Task {
      val name = "es-generic-node"
      override val user = "elasticsearch".some
      val nodeList = dev match {
        case true  => "es1"
        case false => "es1,es2,es3"
      }
      val env = Map("ES_JAVA_OPTS" -> "-Xms8g -Xmx8g").some
      object config extends Config {
        val image = "elasticsearch:7.3.2"
        val command = "bash".some
        val port_map = Map("rest" -> 9200, "transport" -> 9300)
        val volumes = List(
          "/opt/radix/timberland:/timberland"
        ).some
        val hostname = "es${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = None
        val args = List(
          "-c",
          s"ln -s /local/unicast_hosts.txt /usr/share/elasticsearch/config/unicast_hosts.txt; elasticsearch -Ecluster.name=radix-es -Ediscovery.seed_providers=file -Ecluster.initial_master_nodes=${nodeList}"
        ).some
        override val ulimit =
          Map("nofile" -> "65536", "nproc" -> "8192", "memlock" -> "-1").some
      }

      object ESTransport extends Service {
        val tags = List("elasticsearch", "transport")
        val port = "transport".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "transport"
        }
      }

      object ESRest extends Service {
        val tags = List("elasticsearch", "rest")
        val port = "rest".some
        val checks = List(tcpcheck)
        object tcpcheck extends Check {
          val `type` = "tcp"
          val port = "rest"
        }
      }

      val services = List(ESTransport, ESRest)

      object unicastTemplate extends Template {
        override val source =
          "/opt/radix/timberland/nomad/elasticsearch/unicast_hosts.tpl".some
        val destination = "local/unicast_hosts.txt"
      }

      val templates = List(unicastTemplate).some
      object resources extends Resources {
        val cpu = 2500
        val memory = 10000
        object network extends Network {
          val networkPorts = Map("rest" -> 9200.some, "transport" -> 9300.some)
        }
      }
    }
  }

  object KibanaGroup extends Group {
    val name = "kibana"
    val count = 1
    val tasks = List(KibanaTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object KibanaTask extends Task {
      val name = "kibana"
      val env = Map("NODE_OPTIONS" -> "--max-old-space-size=1024").some

      object config extends Config {
        val image =
          "registry.gitlab.com/radix-labs/kibana-gantt"
        val command = "kibana".some
        val port_map = Map("kibana" -> 5601)
        val volumes = None
        val hostname = "${attr.unique.hostname}-em"
        val entrypoint = None
        val args = List("--elasticsearch.hosts=http://elasticsearch-elasticsearch-es-generic-node.service.consul:9200",
          "--server.host=0.0.0.0",
          "--path.data=/alloc/data",
          "--elasticsearch.preserveHost=false",
          "--xpack.apm.ui.enabled=false",
          "--xpack.graph.enabled=false",
          "--xpack.ml.enabled=false").some

        override val ulimit =
          Map("nofile" -> "65536", "nproc" -> "8192", "memlock" -> "-1").some
      }

      object startTemplate extends Template {
        override val source =
          "/opt/radix/timberland/nomad/kibana/start.sh".some
        val destination = "local/start.sh"
        override val perms = "755"
      }

      object KibanaService extends Service {
        val tags = List("kibana", "http")
        val port = "kibana".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "kibana"
        }
      }

      val services = List(KibanaService)
      val templates = None

      object resources extends Resources {
        val cpu = 1024
        val memory = 2048
        object network extends Network {
          val networkPorts =
            Map("kibana" -> 5601.some)
        }
      }
    }
  }

  def jobshim() = JobShim(name, Elasticsearch(dev, quorumSize).assemble)
}