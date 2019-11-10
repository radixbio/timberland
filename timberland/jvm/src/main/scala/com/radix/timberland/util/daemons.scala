package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.{
  GroupShim,
  JobShim,
  PortShim,
  ServiceShim,
  TaskShim
}
import com.radix.utils.helm.NomadHCL.{HCLAble, defs}

trait Update {
  def stagger: String

  def max_parallel: Int

  def min_healthy_time: String

  def assemble: defs.Update =
    defs.Update(stagger = stagger,
                max_parallel = max_parallel,
                min_healthy_time = min_healthy_time)
}

trait Constraint {
  def operator: Option[String]

  def attribute: Option[String]

  def value: String

  def assemble: defs.Constraint = {
    (operator, attribute) match {
      case (Some(op), None) => defs.Constraint(operator = op, value = value)
      case (None, Some(attr)) =>
        defs.Constraint(attribute = attr, value = value)
      case (Some(op), Some(attr)) =>
        defs.Constraint(operator = op, attribute = attr, value = value)
      case (None, None) => throw new Error("Missing operator or attribute")
    }
  }
}

trait Template {
  def source: String

  def destination: String

  def assemble: defs.Template =
    defs.Template(source = source.some,
                  destination = destination,
                  change_mode = change_mode)

  def change_mode: String = "noop"
}

trait Config {
  def image: String

  def hostname: String

  def entrypoint: Option[List[String]]

  def command: Option[String]

  def args: Option[List[String]]

  def port_map: Map[String, Int]

  def volumes: Option[List[String]]

  def assemble: defs.DockerConfig =
    defs.DockerConfig(
      image = image,
      hostname = hostname.some,
      entrypoint = entrypoint,
      command = command,
      args = args,
      port_map = port_map.some,
      network_mode = network_mode.some,
      volumes = volumes
    )

  def network_mode: String = "weave"
}

trait Network {
  def networkPorts: Map[String, Int]

  def assemble: Option[defs.Network] = {
    val portShims: List[PortShim] =
      networkPorts.map(kv => PortShim(kv._1, defs.Port(kv._2.some))).to[List]
    //    for ((key, value) <- networkPorts) {
    //      val shim = PortShim(key, defs.Port(value.some))
    //      finalNetwork = defs.Network(port = shim)
    //    }
    val finalNetwork = defs.Network(ports = portShims)
    finalNetwork.some
  }
}

trait Resources {
  def cpu: Int

  def memory: Int

  def network: Network

  def assemble: defs.Resources =
    defs.Resources(cpu = cpu, memory = memory, network = network.assemble)
}

trait Check {
  def `type`: String

  def port: String

  def assemble: defs.Check =
    defs.Check(port = port.some,
               `type` = `type`,
               timeout = timeout,
               interval = interval)

  def interval: String = "10s"

  def timeout: String = "2s"
}

trait Service {
  def tags: List[String]

  def port: Option[String]

  def checks: List[Check]

  def assemble: ServiceShim = {
    val assembledChecks: Option[List[defs.Check]] = checks.map(_.assemble).some
    ServiceShim(
      defs.Service(tags = tags.some, port = port, check = assembledChecks))
  }
}

trait Task {
  def name: String

  def driver = "docker"

  def template: Option[Template]

  def config: Config

  def resources: Resources

  def env: Option[Map[String, String]]

  def services: List[Service]

  def assemble: TaskShim = {
    val assembledServices = services.map(_.assemble).some
    (env, template) match {
      case (Some(e), None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some))
      case (None, Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           template = t.assemble.some))
      case (Some(e), Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some,
                           template = t.assemble.some))
      case (None, None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble))
    }

  }
}

trait Group {
  def name: String

  def constraints: Option[List[Constraint]]

  def count: Int

  def tasks: List[Task]

  def assemble: GroupShim = {
    val assembledConstraints: Option[List[defs.Constraint]] =
      constraints match {
        case Some(c) => c.map(_.assemble).some
        case None    => None

      }
    val assembledTasks = tasks.map(_.assemble)
    GroupShim(name,
              defs.Group(count = count,
                         constraint = assembledConstraints,
                         task = assembledTasks))

  }
}

trait Job {
  def name: String

  def datacenters: List[String]

  def update: Option[Update] = None

  def constraints: Option[List[Constraint]]

  def groups: List[Group]

  def `type`: Option[String] = "service".some

  def assemble: defs.Job = {
    val assembledConstraints: Option[List[defs.Constraint]] =
      constraints match {
        case Some(c) => c.map(_.assemble).some
        case None    => None

      }
    val assembledGroups: List[GroupShim] = groups.map(_.assemble)

    update match {
      case Some(update) => defs.Job(datacenters = datacenters,
        update = update.assemble.some,
        group = assembledGroups,
        constraint = assembledConstraints, `type` = `type`.getOrElse("service"))
      case None => defs.Job(datacenters = datacenters,
        group = assembledGroups,
        constraint = assembledConstraints, `type` = `type`.getOrElse("service"))
    }

  }
}

object ZookeeperDaemons extends Job {
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
    var count = 3
    val constraints = List(distinctHost).some
    val tasks = List(zookeeper)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    object zookeeper extends Task {
      val name = "zookeeper"
      val template = zookeeperTemplate.some
      val env = None
      val services =
        List(zookeeperClient, zookeeperFollower, zookeeperOthersrvs)

      object zookeeperTemplate extends Template {
        val source =
          "/mnt/timberland/jvm/src/main/resources/nomad/config/zookeeper/zoo.tpl"
        val destination = "local/conf/zoo_servers"
      }

      object config extends Config {
        val image = "zookeeper:3.4"
        val hostname = "${attr.unique.hostname}-zookeeper"
        // CMD and ARGS here should invoke timberland
        //TODO grab timberland from artifactory, not volume mount
        val entrypoint = List(
          "/timberland/jvm/target/universal/stage/bin/timberland").some
        val command = "runtime".some
        var args = List("launch", "zookeeper").some
        val port_map =
          Map("client" -> 2181, "follower" -> 2888, "othersrvs" -> 3888)
        //                dns_servers = ["${attr.unique.network.ip-address}"]
        //TODO swap mount point for artifactory
        val volumes = List(
          "/mnt/timberland/:/timberland"
        ).some
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 2048

        object network extends Network {
          val networkPorts =
            Map("client" -> 2181, "follower" -> 2888, "othersrvs" -> 3888)

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

    }

  }

  val jobshim: JobShim = JobShim(name, ZookeeperDaemons.assemble)
}

object KafkaDaemons extends Job {
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
    var count = 3
    val constraints = List(distinctHost).some
    val tasks = List(kafkaTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    object kafkaTask extends Task {
      val name = "kafka"
      val template = kafkaTemplate.some
      var env = Map("KAFKA_BROKER_ID" -> "${NOMAD_ALLOC_INDEX}",
                    "TOPIC_AUTO_CREATE" -> "true").some
      val services = List(kafkaPlaintext)

      object kafkaTemplate extends Template {
        val source =
          "/mnt/timberland/jvm/src/main/resources/nomad/config/zookeeper/zoo.tpl"
        val destination = "local/conf/zoo_servers"
      }

      object config extends Config {
        val image = "confluentinc/cp-kafka:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka"
        val entrypoint = List(
          "/timberland/jvm/target/universal/stage/bin/timberland").some
        val command = "runtime".some
        var args = List("launch", "kafka").some
        val port_map = Map("kafka" -> 9092)
        val volumes = List("/mnt/timberland/:/timberland").some
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 2048

        object network extends Network {
          val networkPorts = Map("kafka" -> 9092)
        }

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

  val jobshim: JobShim = JobShim(name, KafkaDaemons.assemble)
}

object KafkaCompanionDaemons extends Job {
  val name = "kafka-companion-daemons"
  val datacenters: List[String] = List("dc1")
  val constraints = Some(List(kernelConstraint))
  val groups = List(kafkaCompainions)

  object kafkaCompanionsUpdate extends Update {
    val stagger = "10s"
    val max_parallel = 1
    val min_healthy_time = "10s"
  }

  override val update = kafkaCompanionsUpdate.some

  object kernelConstraint extends Constraint {
    val operator = None
    val attribute = "${attr.kernel.name}".some
    val value = "linux"
  }

  object kafkaCompainions extends Group {
    val name = "kafkaCompanions"
    val count = 1
    val constraints = None
    val tasks = List(schemaRegistry, kafkaRestProxy, kafkaConnect, kSQL)

    object schemaRegistry extends Task {
      val name = "schemaRegistry"
      val env = Map(
//        "SCHEMA_REGISTRY_KAFKASTORE_CONNECTION_URL" -> "zookeeper-daemons-zookeeper-zookeeper.service.consul:2181",
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> "PLAINTEXT://kafka-daemons-kafka-kafka.service.consul:9092",
        "SCHEMA_REGISTRY_HOST_NAME" -> "${attr.unique.hostname}-kafka-schema-registry",
        "SCHEMA_REGISTRY_LISTENERS" -> "http://0.0.0.0:8081"
      ).some

      val services = List(kafkaServiceRegistry)
      val template = None
      object config extends Config {
        val image = "confluentinc/cp-schema-registry:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-schema-registry"
        val dns_servers = List("169.254.1.1")
        val port_map = Map("registry_listener" -> 8081)
        val entrypoint = None
        val command = None
        val args = None
        val volumes = None
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("registry_listener" -> 8081)
        }
      }

      object kafkaServiceRegistry extends Service {
        val tags = List("kafka-companion", "kafka-schema-registry")
        val port = "registry_listener".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "registry_listener"
        }
      }
    }

    object kafkaRestProxy extends Task {
      val name = "kafkaRestProxy"
      val template = None
      val env = Map(
//        "KAFKA_REST_ZOOKEEPER_CONNECT" -> "zookeeper-daemons-zookeeper-zookeeper.service.consul:2181",
        "KAFKA_REST_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
        "KAFKA_REST_SCHEMA_REGISTRY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-schemaRegistry.service.consul:8081",
        "KAFKA_REST_HOST_NAME" -> "${attr.unique.hostname}-kafka-rest-proxy",
        "KAFKA_REST_LISTENERS" -> "http://0.0.0.0:8082"
      ).some
      object config extends Config {
        val image = "confluentinc/cp-kafka-rest:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-rest-proxy"
        val dns_servers = List("169.254.1.1")
        val port_map = Map("rest" -> 8082)
        val entrypoint = None
        val command = None
        val args = None
        val volumes = None
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("rest" -> 8082)
        }
      }

      val services = List(restProxyService)

      object restProxyService extends Service {
        val tags = List("kafka-companion", "kafka-rest-proxy")
        val port = "rest".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "rest"
        }
      }
    }

    object kafkaConnect extends Task {
      val name = "kafkaConnect"
      val template = None
      var env = Map(
        "CONNECT_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
        "CONNECT_REST_PORT" -> "8083",
        "CONNECT_GROUP_ID" -> "kafka-daemons-connect-group",
        "CONNECT_CONFIG_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-configs",
        "CONNECT_OFFSET_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-offsets",
        "CONNECT_STATUS_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-status",
        "CONNECT_KEY_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
        "CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-schemaRegistry:8081",
        "CONNECT_VALUE_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
        "CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-schemaRegistry:8081",
        "CONNECT_INTERNAL_KEY_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
        "CONNECT_INTERNAL_VALUE_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
        "CONNECT_REST_ADVERTISED_HOST_NAME" -> "${attr.unique.hostname}-kafka-connect",
        "CONNECT_LOG4J_ROOT_LOGLEVEL" -> "INFO",
        "CONNECT_LOG4J_LOGGERS" -> "org.apache.kafka.connect.runtime.rest=WARN,org.reflections=ERROR",
        "CONNECT_PLUGIN_PATH" -> "/usr/share/java,/etc/kafka-connect/jars",
        "KAFKA_REST_PROXY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-kafkaRestProxy.service.consul:8082",
        "PROXY" -> "true"
      ).some

      val prodEnv = Map(
        "CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR" -> "3",
        "CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR" -> "3",
        "CONNECT_STATUS_STORAGE_REPLICATION_FACTOR" -> "3",
      )

      val devEnv = Map(
        "CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR" -> "1",
        "CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR" -> "1",
        "CONNECT_STATUS_STORAGE_REPLICATION_FACTOR" -> "1",
      )
      object config extends Config {
        val image = "confluentinc/cp-kafka-connect:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-connect"
        val port_map = Map("connect" -> 8083)
        val entrypoint = None
        val command = None
        val args = None
        val volumes = None
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 2000
        object network extends Network {
          val networkPorts = Map("connect" -> 8083) //TODO: This differs from the standard
        }
      }

      val services = List(connectService)

      object connectService extends Service {
        val tags = List("kafka-companion", "kafka-connect")
        val port = "connect".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "connect"
        }
      }
    }

    object kSQL extends Task {
      val name = "kSQL"
      val template = None
      val env = Map(
        "KAFKA_REST_PROXY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-kafkaRestProxy.service.consul:8082",
        "KSQL_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
        "KSQL_LISTENERS" -> "http://0.0.0.0:8000",
        "KSQL_KQSL_SERVICE_ID" -> "ksql-server",
        "PROXY" -> "true"
      ).some
      object config extends Config {
        val image = "confluentinc/cp-ksql-server:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-ksql-server"
        val port_map = Map("ui" -> 8000)
        val entrypoint = None
        val command = None
        val args = None
        val volumes = None
      }

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("ui" -> 8000) //TODO: This differs from the standard
        }
      }

      val services = List(restProxyService)

      object restProxyService extends Service {
        val tags = List("kafka-companion", "kafka-topics-ui")
        val port = "ui".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "ui"
        }
      }
    }

  }

  val jobshim: JobShim = JobShim(name, KafkaCompanionDaemons.assemble)
}
