resource "nomad_job" "yb_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/yugabyte_kafka_connector/yugabyte_kafka_connector.tmpl", {namespace = var.namespace})
}
