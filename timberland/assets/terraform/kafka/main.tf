resource "nomad_job" "kafka" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka/kafka.tmpl", {prefix = var.prefix, test = var.test, dev = var.dev, quorum_size = var.quorum_size, interbroker_port = var.interbroker_port, zk_addr = join(",", var.zookeeper_address)})
}

data "consul_service_health" "kafka_health" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}kafka-daemons-kafka-kafka"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}