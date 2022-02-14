resource "nomad_job" "es_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/es_kafka_connector/es_kafka_connector.tmpl", {namespace = var.namespace})
}
