resource "nomad_job" "kafka_companions" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka_companions/kafka_companions.tmpl", {namespace = var.namespace, test = var.test, dev = var.dev, quorum_size = var.quorum_size, interbroker_port = var.interbroker_port})
}

data "consul_service_health" "schema_registry_health" {
  count = var.enable ? 1 : 0
  name = "kc-schema-registry-service-0"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "connect_health" {
  count = var.enable ? 1 : 0
  name = "kc-connect-service-0"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "rest_proxy_health" {
  count = var.enable ? 1 : 0
  name = "kc-rest-proxy-service-0"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}

data "consul_service_health" "ksql_health" {
  count = var.enable ? 1 : 0
  name = "kc-ksql-service-0"
  passing = true
  depends_on = [nomad_job.kafka_companions]
  wait_for = "300s"
}
