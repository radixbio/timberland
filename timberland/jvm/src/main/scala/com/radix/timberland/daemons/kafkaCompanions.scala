package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.JobShim

case class KafkaCompanionDaemons(dev: Boolean,
                                 servicePort: Int = 9092,
                                 registryListenerPort: Int = 8081)
  extends Job {
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
//                "SCHEMA_REGISTRY_KAFKASTORE_CONNECTION_URL" -> "zookeeper-daemons-zookeeper-zookeeper.service.consul:2181",
        //This is a hack, note the 2
        //https://github.com/confluentinc/schema-registry/issues/648
        "SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR" -> {if (dev) "1" else "3"},
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> s"PLAINTEXT://kafka-daemons-kafka-kafka.service.consul:2${servicePort}",
        "SCHEMA_REGISTRY_HOST_NAME" -> "${attr.unique.hostname}-kafka-schema-registry",
        "SCHEMA_REGISTRY_LISTENERS" -> (s"http://0.0.0.0:${registryListenerPort}")
      ).some

      val services = List(kafkaServiceRegistry)
      object config extends Config {
        val image = "confluentinc/cp-schema-registry:5.3.1"
        val hostname = "${attr.unique.hostname}-kafka-schema-registry"
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
          val networkPorts = Map(
            "registry_listener" -> registryListenerPort.some)
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
      val templates = None
      val env = dev match {
        case true =>
          Map(
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
        case false =>
          Map(
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

  def jobshim(): JobShim =
    JobShim(
      name,
      KafkaCompanionDaemons(dev, servicePort, registryListenerPort).assemble)
}