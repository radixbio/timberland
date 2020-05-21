terraform {
  backend "consul" {
    address = "consul.service.consul:8500"
    scheme  = "http"
    path    = "terraform"
  }
}

provider "consul" {
  address = "http://consul.service.consul:8500"
  version = "~> 2.7"
}

provider "nomad" {
  address = "http://nomad.service.consul:4646"
  version = "~> 1.4"
}

provider "null" {
  version = "~> 2.1"
}

resource "nomad_job" "apprise" {
  count = var.launch_apprise ? 1 : 0
  depends_on = [nomad_job.vault]
  jobspec = templatefile("./templates/apprise.tmpl", {integration = var.test, prefix = var.prefix, test = var.test})
}

resource "nomad_job" "elasticsearch" {
  count = var.launch_es ? 1 : 0
  jobspec = templatefile("./templates/elastic_search.tmpl", {quorum_size = var.elastic_search_quorum_size, dev = var.dev, prefix = var.prefix, test = var.test})
}

data "consul_service_health" "es_health" {
  count = var.launch_es ? 1 : 0
  name = "elasticsearch-elasticsearch-es-generic-node"
  passing = true
  depends_on = [nomad_job.elasticsearch]
  wait_for = "300s"
}

data "consul_service_health" "kibana_health" {
  count = var.launch_es ? 1 : 0
  name = "elasticsearch-kibana-kibana"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}

resource "null_resource" "es_health_data" {
  count = var.launch_es ? 1 : 0
  triggers = {
    upstream = length(data.consul_service_health.es_health[count.index].results) > 0 && length(data.consul_service_health.kibana_health[count.index].results) > 0 ? 1 : 0
  }
}

resource "nomad_job" "elemental" {
  count = var.launch_elemental ? 1 : 0
  depends_on = [null_resource.kafka_health_data]
  jobspec = templatefile("./templates/elemental.tmpl", {prefix = var.prefix, test = var.test, quorum_size = var.elemental_quorum_size, token = var.elemental_vault_token})
}

resource "nomad_job" "es_kafka_connector" {
  count = var.launch_es && var.launch_kafka ? 1 : 0
  depends_on = [null_resource.kafka_health_data, null_resource.es_health_data]
  jobspec = templatefile("./templates/es_kafka_connector.tmpl", {prefix = var.prefix, test = var.test})
}

resource "nomad_job" "kafka" {
  count = var.launch_kafka ? 1 : 0
  depends_on = [null_resource.zookeeper_health_data]
  jobspec = templatefile("./templates/kafka.tmpl", {prefix = var.prefix, test = var.test, dev = var.dev, quorum_size = var.kafka_quorum_size, interbroker_port = var.kafka_interbroker_port})
}

data "consul_service_health" "kafka_health" {
  count = var.launch_kafka ? 1 : 0
  name = "kafka-daemons-kafka-kafka"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}

resource "null_resource" "kafka_health_data" {
  count = var.launch_kafka ? 1 : 0
  triggers = {
    upstream = length(data.consul_service_health.kafka_health[count.index].results) > 1 ? 1 : 0
  }
}

resource "nomad_job" "kafka_companions" {
  count = var.launch_kafka_companions ? 1 : 0
  depends_on = [null_resource.kafka_health_data]
  jobspec = templatefile("./templates/kafka_companions.tmpl", {prefix = var.prefix, test = var.test, dev = var.dev, quorum_size = var.kafka_companions_quorum_size, interbroker_port = var.kafka_interbroker_port})
}

resource "nomad_job" "minio" {
  count = var.launch_minio ? 1 : 0
  depends_on = [null_resource.kafka_health_data]
  jobspec = templatefile("./templates/minio.tmpl", {prefix = var.prefix, test = var.test, upstream_access_key = var.minio_upstream_access_key, upstream_secret_key = var.minio_upstream_secret_key})
}

resource "nomad_job" "retool" {
  count = var.launch_retool ? 1 : 0
  depends_on = [nomad_job.vault]
  jobspec = templatefile("./templates/retool.tmpl", {prefix = var.prefix, test = var.test, })
}

data "consul_service_health" "retool_health" {
  count = var.launch_retool ? 1 : 0
  name = "retool-retool-retool-main"
  passing = true
  depends_on = [nomad_job.retool]
  wait_for = "300s"
}

data "consul_service_health" "postgres_health" {
  count = var.launch_retool ? 1 : 0
  name = "retool-retool-postgres"
  passing = true
  depends_on = [nomad_job.retool]
  wait_for = "300s"
}

resource "null_resource" "retool_health_data" {
  count = var.launch_retool ? 1 : 0
  triggers = {
    upstream = length(data.consul_service_health.retool_health) > 0 && length(data.consul_service_health.postgres_health) > 0 ? 1 : 0
  }
}

resource "nomad_job" "pg_kafka_connector" {
  count = var.launch_retool && var.launch_kafka ? 1 : 0
  depends_on = [null_resource.kafka_health_data, null_resource.retool_health_data]
  jobspec = templatefile("./templates/retool_pg_kafka_connector.tmpl", {prefix = var.prefix, test = var.test})
}

//TODO make vault actually use the quorum size
resource "nomad_job" "vault" {
  count = var.launch_vault ? 1 : 0
  jobspec = templatefile("./templates/vault.tmpl", {prefix = var.prefix, test = var.test, quorum_size = var.vault_quorum_size})
}

resource "nomad_job" "yugabyte" {
  count = var.launch_yugabyte ? 1 : 0
  jobspec = templatefile("./templates/yugabyte.tmpl", {quorum_size = var.yugabyte_quorum_size, test = var.test, prefix = var.prefix, dev = var.dev})
}

data "consul_service_health" "yb_master_health" {
  count = var.launch_yugabyte ? 1 : 0
  name = "yugabyte-yugabyte-ybmaster"
  passing = true
  depends_on = [nomad_job.yugabyte]
  wait_for = "300s"
}

data "consul_service_health" "yb_tserver_health" {
  count = var.launch_yugabyte ? 1 : 0
  name = "yugabyte-yugabyte-ybtserver"
  passing = true
  depends_on = [nomad_job.yugabyte]
  wait_for = "300s"
}

resource "null_resource" "yb_health_data" {
  count = var.launch_yugabyte ? 1 : 0
  triggers = {
    upstream = length(data.consul_service_health.yb_master_health[count.index].results) > 0 && length(data.consul_service_health.yb_tserver_health[count.index].results) > 0 ? 1 : 0
  }
}

resource "nomad_job" "yb_kafka_connector" {
  count = var.launch_yugabyte && var.launch_kafka ? 1 : 0
  depends_on = [null_resource.yb_health_data, null_resource.kafka_health_data]
  jobspec = templatefile("./templates/yugabyte_kafka_connector.tmpl", {prefix = var.prefix, test = var.test, })
}

resource "nomad_job" "zookeeper" {
  count = var.launch_zookeeper ? 1 : 0
  depends_on = [nomad_job.vault]
  jobspec = templatefile("./templates/zookeeper.tmpl", {dev = var.dev, prefix = var.prefix, test = var.test, quorum_size = var.zookeeper_quorum_size})
}

data "consul_service_health" "zookeeper_health" {
  count = var.launch_zookeeper ? 1 : 0
  name = "zookeeper-daemons-zookeeper-zookeeper"
  passing = true
  depends_on = [nomad_job.zookeeper]
  wait_for = "300s"
}

resource "null_resource" "zookeeper_health_data" {
  count = var.launch_zookeeper ? 1 : 0
  triggers = {
    upstream = length(data.consul_service_health.zookeeper_health[count.index].results) > 0 ? 1 : 0
  }
}
