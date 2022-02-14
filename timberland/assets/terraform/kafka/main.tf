resource "nomad_job" "kafka" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka/kafka.tmpl", {namespace = var.namespace, dev = var.dev, config = var.config, interbroker_port = var.interbroker_port})
}

data "consul_service_health" "kafka_health" {
  count = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  name = "kafka-${count.index}"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}
