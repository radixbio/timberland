resource "nomad_job" "yb_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/yugabyte_kafka_connector/yugabyte_kafka_connector.tmpl", {prefix = var.prefix, test = var.test, schema_registry_address = var.schema_registry_address, connect_address = var.connect_address, tserver_address = var.yb_tserver_address})
}
