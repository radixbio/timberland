resource "nomad_job" "pg_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/retool_pg_kafka_connector/retool_pg_kafka_connector.tmpl", {prefix = var.prefix, test = var.test})
}