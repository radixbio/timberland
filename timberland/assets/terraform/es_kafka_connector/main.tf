resource "nomad_job" "es_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/es_kafka_connector/es_kafka_connector.tmpl", {prefix = var.prefix, test = var.test, schema_registry_address = var.schema_registry_address, es_address = var.elasticsearch_address, connect_address = var.connect_address})
}
