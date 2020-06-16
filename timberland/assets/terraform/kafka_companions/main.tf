resource "nomad_job" "kafka_companions" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/kafka_companions/kafka_companions.tmpl", {prefix = var.prefix, test = var.test, dev = var.dev, quorum_size = var.quorum_size, interbroker_port = var.interbroker_port, kafka_address = var.kafka_address})
}

data "consul_service_health" "schema_registry_health" {
  count = var.enable ? 1 : 0
  name = "kafka-companion-daemons-kafkaCompanions-schemaRegistry"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "connect_health" {
  count = var.enable ? 1 : 0
  name = "kafka-companion-daemons-kafkaCompanions-kafkaConnect"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "rest_proxy_health" {
  count = var.enable ? 1 : 0
  name = "kafka-companion-daemons-kafkaCompanions-kafkaRestProxy"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "ksql_health" {
  count = var.enable ? 1 : 0
  name = "kafka-companion-daemons-kafkaCompanions-kSQL"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}