package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.{
  GroupShim,
  JobShim,
  PortShim,
  ServiceShim,
  TaskShim,
TemplateShim
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
  def source: Option[String] = None

  def destination: String

  val data: Option[String] = None

  val perms: String = "644"

  def assemble: TemplateShim =
    TemplateShim(defs.Template(source = source,
                  destination = destination,
                  change_mode = change_mode,
                  data = data,
                  perms = perms))

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

  def cap_add: Option[List[String]] = List().some

  def ulimit: Option[Map[String, String]] = None

  def privileged: Option[Boolean] = false.some

  def assemble: defs.DockerConfig =
    defs.DockerConfig(
      image = image,
      hostname = hostname.some,
      entrypoint = entrypoint,
      command = command,
      args = args,
      port_map = port_map.some,
      network_mode = network_mode.some,
      volumes = volumes,
      cap_add = cap_add,
      ulimit = ulimit,
      privileged = privileged
    )

  def network_mode: String = "weave"
}

trait Network {
  def networkPorts: Map[String, Option[Int]]

  def assemble: Option[defs.Network] = {
    val portShims: List[PortShim] =
      networkPorts.map(kv => PortShim(kv._1, defs.Port(kv._2))).to[List]
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

  def name: Option[String] = None

  def assemble: ServiceShim = {
    val assembledChecks: Option[List[defs.Check]] = checks.map(_.assemble).some
    ServiceShim(
      defs.Service(tags = tags.some,
                   port = port,
                   name = name,
                   check = assembledChecks))
  }
}

trait Task {
  def name: String

  def driver = "docker"

  def templates: Option[List[Template]]

  def config: Config

  def resources: Resources

  def env: Option[Map[String, String]]

  def services: List[Service]

  def user: Option[String] = None

  def assemble: TaskShim = {
    val assembledServices = services.map(_.assemble).some
    val assembledTemplates = templates.getOrElse(List()).map(_.assemble).some
    (env, assembledTemplates) match {
      case (Some(e), None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some,
                           user = user))
      case (None, Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           template = assembledTemplates,
                           user = user))
      case (Some(e), Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some,
                           template = assembledTemplates,
                           user = user))
      case (None, None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           user = user))
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
      case Some(update) =>
        defs.Job(datacenters = datacenters,
                 update = update.assemble.some,
                 group = assembledGroups,
                 constraint = assembledConstraints,
                 `type` = `type`.getOrElse("service"))
      case None =>
        defs.Job(datacenters = datacenters,
                 group = assembledGroups,
                 constraint = assembledConstraints,
                 `type` = `type`.getOrElse("service"))
    }

  }
}

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
      val template = zookeeperTemplate.some
      val env = None
      val services =
        List(zookeeperClient, zookeeperFollower, zookeeperOthersrvs)

      object zookeeperTemplate extends Template {
        override val source =
          "/mnt/timberland/jvm/src/main/resources/nomad/config/zookeeper/zoo.tpl".some
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
        var args = dev match {
          case true => List("launch", "zookeeper", "--dev").some
          case false => List("launch", "zookeeper").some
        }
        val port_map =
          Map("client" -> 2181, "follower" -> 2888, "othersrvs" -> 3888)
        //                dns_servers = ["${attr.unique.network.ip-address}"]
        //TODO swap mount point for artifactory
        val volumes = List(
          "/mnt/timberland/:/timberland"
        ).some
      }
      val templates = List(zookeeperTemplate).some
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

    }

  }

  def jobshim(): JobShim = JobShim(name, ZookeeperDaemons(dev, quorumSize).assemble)
}

case class KafkaDaemons(dev: Boolean, quorumSize: Int, servicePort: Int = 9092) extends Job {
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
      var env: Option[Map[String, String]] = dev match {
        case true => Map("KAFKA_BROKER_ID" -> "${NOMAD_ALLOC_INDEX}",
            "TOPIC_AUTO_CREATE" -> "true").some
        case false => Map("KAFKA_BROKER_ID" -> "${NOMAD_ALLOC_INDEX}",
          "TOPIC_AUTO_CREATE" -> "true", "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1").some
      }
      val services = List(kafkaPlaintext)

      object kafkaTemplate extends Template {
        override val source =
          "/mnt/timberland/jvm/src/main/resources/nomad/config/zookeeper/zoo.tpl".some
        val destination = "local/conf/zoo_servers"
      }
      val templates = List(kafkaTemplate).some
      object config extends Config {
        val image = "confluentinc/cp-kafka:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka"
        val entrypoint = List(
          "/timberland/jvm/target/universal/stage/bin/timberland").some
        val command = "runtime".some
        var args = dev match {
          case true => List("launch", "kafka", "--dev").some
          case false => List("launch", "kafka").some
        }
        val port_map = Map("kafka" -> servicePort)
        val volumes = List("/mnt/timberland/:/timberland").some
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

  def jobshim(): JobShim = JobShim(name, KafkaDaemons(dev, quorumSize, servicePort).assemble)
}

case class VaultDaemon(dev: Boolean, quorumSize: Int) extends Job {
  val name = "vault-daemon"
  val datacenters: List[String] = List("dc1")

  object VaultDaemonUpdate extends Update {
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
  override val update = VaultDaemonUpdate.some

  val groups = List(VaultGroup)

  object VaultGroup extends Group {
    val name = "vault"
    val count = quorumSize
    val tasks = List(VaultTask)

    object distinctHost extends Constraint {
      val operator = "distinct_hosts".some
      val attribute = None
      val value = "true"
    }

    val constraints = List(distinctHost).some

    object VaultTask extends Task {
      val name = "vault"
      val env = Map("" -> "").some
//      val env = Map("VAULT_LOCAL_CONFIG" -> "\n                storage \"consul\" {\n                    address = \"127.0.0.1:8500\"\n                    path = \"vault\"\n                }\n                listener \"tcp\" {\n                    address = \"127.0.0.1:8200\"\n                \n                }\n                telemetry {\n                    statsite_address = \"127.0.0.1:8125\"\n                }\n                plugin-directory = \"/opt/plugins/\"\n                ").some
      object config extends Config {
        val image = "registry.gitlab.com/radix-labs/monorepo/vault"
        val command = "vault".some
        val port_map = Map("vault_listen" -> 8200, "telemetry" -> 8125)
        override val cap_add = List("IPC_LOCK").some
        val volumes = None
        val hostname = "${attr.unique.hostname}-vault"
        val entrypoint = None
        val args = List("server", "-config", "/opt/vault_config.conf").some
        override val privileged = true.some
      }
      object VaultListen extends Service {
        val tags = List("vault", "vault-listen")
        val port = "vault_listen".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "vault_listen"
        }
      }

      val services = List(VaultListen)
      val templates = None

      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts =
            Map("vault_listen" -> 8200.some, "telemetry" -> 8125.some)
        }
      }
    }
  }
  def jobshim() = JobShim(name, VaultDaemon(dev, quorumSize).assemble)
}

case class KafkaCompanionDaemons(dev: Boolean, servicePort: Int = 9092, registryListenerPort: Int = 8081) extends Job {
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
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> (s"PLAINTEXT://kafka-daemons-kafka-kafka.service.consul:${servicePort}"),
        "SCHEMA_REGISTRY_HOST_NAME" -> "${attr.unique.hostname}-kafka-schema-registry",
        "SCHEMA_REGISTRY_LISTENERS" -> (s"http://0.0.0.0:${registryListenerPort}")
      ).some

      val services = List(kafkaServiceRegistry)
      object config extends Config {
        val image = "confluentinc/cp-schema-registry:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-schema-registry"
        val dns_servers = List("169.254.1.1")
        val port_map = Map("registry_listener" -> registryListenerPort)
        val entrypoint = None
        val command = None
        val args = None
        val volumes = None
      }
      val templates = None
      object resources extends Resources {
        val cpu = 1000
        val memory = 1000
        object network extends Network {
          val networkPorts = Map("registry_listener" -> registryListenerPort.some)
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
      val templates = None
      val env = Map(
//        "KAFKA_REST_ZOOKEEPER_CONNECT" -> "zookeeper-daemons-zookeeper-zookeeper.service.consul:2181",
        "KAFKA_REST_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
        "KAFKA_REST_SCHEMA_REGISTRY_URL" -> s"http://kafka-companion-daemons-kafkaCompanions-schemaRegistry.service.consul:${registryListenerPort}",
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
          val networkPorts = Map("rest" -> 8082.some)
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
      val templates = None
      val env = dev match {
        case true => Map(
          "CONNECT_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
          "CONNECT_REST_PORT" -> "8083",
          "CONNECT_GROUP_ID" -> "kafka-daemons-connect-group",
          "CONNECT_CONFIG_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-configs",
          "CONNECT_OFFSET_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-offsets",
          "CONNECT_STATUS_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-status",
          "CONNECT_KEY_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
          "CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL" -> s"http://kafka-companion-daemons-kafkaCompanions-kafkaSchemaRegistry:${registryListenerPort}",
          "CONNECT_VALUE_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
          "CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL" -> s"http://kafka-companion-daemons-kafkaCompanions-kafkaSchemaRegistry:${registryListenerPort}",
          "CONNECT_INTERNAL_KEY_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
          "CONNECT_INTERNAL_VALUE_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
          "CONNECT_REST_ADVERTISED_HOST_NAME" -> "${attr.unique.hostname}-kafka-connect",
          "CONNECT_LOG4J_ROOT_LOGLEVEL" -> "INFO",
          "CONNECT_LOG4J_LOGGERS" -> "org.apache.kafka.connect.runtime.rest=WARN,org.reflections=ERROR",
          "CONNECT_PLUGIN_PATH" -> "/usr/share/java,/etc/kafka-connect/jars",
          "KAFKA_REST_PROXY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-kafkaRestProxy.service.consul:8082",
          "PROXY" -> "true",
          "CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR" -> "1",
      "CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR" -> "1",
      "CONNECT_STATUS_STORAGE_REPLICATION_FACTOR" -> "1",
        ).some
        case false => Map(
          "CONNECT_BOOTSTRAP_SERVERS" -> "INSIDE://kafka-daemons-kafka-kafka.service.consul:29092",
          "CONNECT_REST_PORT" -> "8083",
          "CONNECT_GROUP_ID" -> "kafka-daemons-connect-group",
          "CONNECT_CONFIG_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-configs",
          "CONNECT_OFFSET_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-offsets",
          "CONNECT_STATUS_STORAGE_TOPIC" -> "kafka-connect-daemons-connect-status",
          "CONNECT_KEY_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
          "CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL" -> s"http://kafka-companion-daemons-kafkaCompanions-kafkaSchemaRegistry:${registryListenerPort}",
          "CONNECT_VALUE_CONVERTER" -> "io.confluent.connect.avro.AvroConverter",
          "CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL" -> s"http://kafka-companion-daemons-kafkaCompanions-kafkaSchemaRegistry:${registryListenerPort}",
          "CONNECT_INTERNAL_KEY_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
          "CONNECT_INTERNAL_VALUE_CONVERTER" -> "org.apache.kafka.connect.json.JsonConverter",
          "CONNECT_REST_ADVERTISED_HOST_NAME" -> "${attr.unique.hostname}-kafka-connect",
          "CONNECT_LOG4J_ROOT_LOGLEVEL" -> "INFO",
          "CONNECT_LOG4J_LOGGERS" -> "org.apache.kafka.connect.runtime.rest=WARN,org.reflections=ERROR",
          "CONNECT_PLUGIN_PATH" -> "/usr/share/java,/etc/kafka-connect/jars",
          "KAFKA_REST_PROXY_URL" -> "http://kafka-companion-daemons-kafkaCompanions-kafkaRestProxy.service.consul:8082",
          "PROXY" -> "true",
          "CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR" -> "3",
      "CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR" -> "3",
      "CONNECT_STATUS_STORAGE_REPLICATION_FACTOR" -> "3",
      ).some
      }

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
          val networkPorts = Map("connect" -> 8083.some) //TODO: This differs from the standard
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
      val templates = None
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
          val networkPorts = Map("ui" -> 8000.some) //TODO: This differs from the standard
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

  def jobshim(): JobShim = JobShim(name, KafkaCompanionDaemons(dev, servicePort, registryListenerPort).assemble)
}

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

  val groups = List(ElasticsearchGroup)

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
      val env = Map("ES_JAVA_OPTS" -> "-Xms8g -Xmx8g").some
      object config extends Config {
        val image = "elasticsearch:7.3.2"
        val command = "/local/start.sh".some
        val port_map = Map("rest" -> 9200, "transport" -> 9300)
        //        override val cap_add = List("IPC_LOCK").some
        val volumes = None
        val hostname = "es${NOMAD_ALLOC_INDEX+1}"
        val entrypoint = None
        val args = List(
          "-Ebootstrap.memory_lock=true",
          "-Ecluster.name=radix-es",
          "-Ediscovery.seed_providers=file",
          "-Ecluster.initial_master_nodes=es1,es2,es3",
          "-Expack.license.self_generated.type=basic"
        ).some
        //override val ulimit = Map("nofie" -> "65536", "noproc" -> "8192").some //TODO: Doesnt have memlock
      }

      object ESTransport extends Service {
        override val name = "${NOMAD_GROUP_NAME}-discovery".some
        val tags = List("elasticsearch", "transport")
        val port = "transport".some
        val checks = List(check)
        object check extends Check {
          val `type` = "tcp"
          val port = "transport"
        }
      }

      object ESRest extends Service {
        override val name = "${NOMAD_GROUP_NAME}".some
        val tags = List("elasticsearch", "rest")
        val port = "rest".some
        val checks = List(tcpcheck)//, httpcheck)
        object tcpcheck extends Check {
          val `type` = "tcp"
          val port = "rest"
        }
        object httpcheck extends Check {
          val `type` = "http"
          val port = "rest"
        }

      }

      val services = List(ESTransport, ESRest)
      object startTemplate extends Template {
        val destination = "local/start.sh"
        override val perms = "755"
        override val data =
          """<<EOF\nln -s /local/unicast_hosts.txt /usr/share/elasticsearch/config/unicast_hosts.txt\nelasticsearch $@\nEOF""".some
      }

      object unicastTemplate extends Template {
        val destination = "local/unicast_hosts.txt"
        override val data =
          """<<EOF\n{{- range service (printf \"%s-discovery|passing\" (env \"NOMAD_GROUP_NAME\")) }}\n{{ .Address }}:{{ .Port }}{{ end }}\nEOF""".some
      }

      val templates = List(startTemplate, unicastTemplate).some

      object resources extends Resources {
        val cpu = 2500
        val memory = 10000
        object network extends Network {
          val networkPorts = Map("rest" -> None, "transport" -> None)
        }
      }
    }
  }
  def jobshim() = JobShim(name, Elasticsearch(dev, quorumSize).assemble)
}

//object main extends App {
//  val shim = Elasticsearch.jobshim
//  println(shim.asHCL)
//}