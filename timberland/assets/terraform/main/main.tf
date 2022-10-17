terraform {
  backend "consul" {
    scheme  = "http"
    path    = "terraform"
  }
}

provider "consul" {
  address = "http://${var.consul_address}:8500"
  version = "~> 2.7"
  token = var.acl_token
}

provider "nomad" {
  address = "http://${var.nomad_address}:4646"
  version = "~> 1.4"
  secret_id = var.acl_token
}

provider "vault" {
  // NOTE: This makes the assumption that vaultAddr == consulAddr
  address = "http://${var.consul_address}:8200"
  version = "2.11.0"
}

module "apprise" {
  enable = contains(var.feature_flags, "apprise")
  // count is not supported for modules yet - https://github.com/hashicorp/terraform/issues/17519
  // instead, we have to pass a boolean and use count on each resource within the module
  // but the count variable inside modules is coming in terraform 0.13!!
  
//  count = var.launch_apprise ? 1 : 0
  source = "/opt/radix/timberland/terraform/apprise"

  test = var.test
  prefix = var.prefix
}

module "elasticsearch" {
  enable = contains(var.feature_flags, "elasticsearch")

  source = "/opt/radix/timberland/terraform/elasticsearch"

  dev = contains(var.feature_flags, "dev")
  test = var.test
  prefix = var.prefix
}

module "elemental" {
  enable = contains(var.feature_flags, "elemental")

  source = "/opt/radix/timberland/terraform/elemental"

  dev = contains(var.feature_flags, "dev")
  test = var.test
  prefix = var.prefix
}

module "es_kafka_connector" {
  enable = contains(var.feature_flags, "es_kafka_connector")

  source = "/opt/radix/timberland/terraform/es_kafka_connector"

  test = var.test
  prefix = var.prefix
  schema_registry_address = module.kafka_companions.schema_registry_health_result
  elasticsearch_address = module.elasticsearch.elasticsearch_health_result
  connect_address = module.kafka_companions.connect_health_result
}

module "kafka" {
  enable = contains(var.feature_flags, "kafka")

  source = "/opt/radix/timberland/terraform/kafka"

  dev = contains(var.feature_flags, "dev")
  test = var.test
  prefix = var.prefix
  quorum_size = var.kafka_quorum_size
  interbroker_port = var.kafka_interbroker_port

  zookeeper_address = module.zookeeper.zookeeper_health_result
}

module "kafka_companions" {
  enable = contains(var.feature_flags, "kafka_companions")

  source = "/opt/radix/timberland/terraform/kafka_companions"

  dev = contains(var.feature_flags, "dev")
  test = var.test
  prefix = var.prefix
  quorum_size = var.kafka_companions_quorum_size
  interbroker_port = var.kafka_interbroker_port

  kafka_address = module.kafka.kafka_health_result
}

module "minio" {
  enable = contains(var.feature_flags, "minio")

  source = "/opt/radix/timberland/terraform/minio"

  prefix = var.prefix
  test = var.test
  kafka_address = module.kafka.kafka_health_result
  have_upstream_creds = contains(var.defined_config_vars, "minio.aws_access_key_id") && contains(var.defined_config_vars, "minio.aws_secret_access_key")
}

module "retool_pg_kafka_connector" {
  enable = contains(var.feature_flags, "retool_pg_kafka_connector")

  source = "/opt/radix/timberland/terraform/retool_pg_kafka_connector"

  test = var.test
  prefix = var.prefix
  schema_registry_address = module.kafka_companions.schema_registry_health_result
  connect_address = module.kafka_companions.connect_health_result
  postgres_address = module.retool_postgres.postgres_health_result
}

module "retool_postgres" {
  enable = contains(var.feature_flags, "retool_postgres")

  source = "/opt/radix/timberland/terraform/retool"

  dev = contains(var.feature_flags, "dev")
  test = var.test
  prefix = var.prefix
}

module "runtime" {
  enable = contains(var.feature_flags, "runtime")
  source = "/opt/radix/timberland/terraform/runtime"

  test = var.test
  prefix = var.prefix
}

module "s3lts" {
  enable = contains(var.feature_flags, "s3lts")
  source = "/opt/radix/timberland/terraform/s3lts"

  test = var.test
  prefix = var.prefix
}

module "yugabyte" {
  enable = contains(var.feature_flags, "yugabyte")

  source = "/opt/radix/timberland/terraform/yugabyte"

  dev = contains(var.feature_flags, "dev")
  prefix = var.prefix
  test = var.test
  quorum_size = var.yugabyte_quorum_size
}

module "yb_kafka_connector" {
  enable = contains(var.feature_flags, "yb_kafka_connector")

  source = "/opt/radix/timberland/terraform/yugabyte_kafka_connector"

  test = var.test
  prefix = var.prefix
  schema_registry_address = module.kafka_companions.schema_registry_health_result
  connect_address = module.kafka_companions.connect_health_result
  yb_tserver_address = module.yugabyte.yb_tserver_health_result
}

module "zookeeper" {
  enable = contains(var.feature_flags, "zookeeper")

  source = "/opt/radix/timberland/terraform/zookeeper"

  test = var.test
  dev = contains(var.feature_flags, "dev")
  prefix = var.prefix
  quorum_size = var.zookeeper_quorum_size
}

