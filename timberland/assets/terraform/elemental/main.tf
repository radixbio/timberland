resource "nomad_job" "elemental" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/elemental/elemental.tmpl", {prefix = var.prefix, test = var.test, quorum_size = var.quorum_size, kafka_address = var.kafka_address, schema_registry_address = var.schema_registry_address})
}

data "consul_service_health" "elemental_health" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}elemental-machines-em-em"
  passing = true
  depends_on = [nomad_job.elemental]
  wait_for = "300s"
}
