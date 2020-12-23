resource "nomad_job" "kafka" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka/kafka.tmpl", {prefix = var.prefix, test = var.test, dev = var.dev, quorum_size = var.quorum_size, interbroker_port = var.interbroker_port, zookeeper_quorum_size = var.zookeeper_quorum_size})
}

data "consul_service_health" "kafka_health" {
  count = var.enable ? (var.dev ? 1 : var.quorum_size) : 0
  name = "kafka-${count.index}"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}
