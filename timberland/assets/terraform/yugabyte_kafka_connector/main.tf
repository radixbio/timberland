resource "nomad_job" "yb_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka_connect_yugabyte/yugabyte_kafka_connector.tmpl", {
    datacenter = var.datacenter,
    namespace = var.namespace
  })
}
