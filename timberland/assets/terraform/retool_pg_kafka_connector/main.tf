resource "nomad_job" "pg_kafka_connector" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/retool_pg_kafka_connector/retool_pg_kafka_connector.tmpl", {prefix = var.prefix, test = var.test, postgres_address = var.postgres_address, connect_address = var.connect_address, schema_registry_address = var.schema_registry_address})
}