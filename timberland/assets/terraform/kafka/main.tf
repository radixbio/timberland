resource "nomad_job" "kafka" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/kafka/kafka.tmpl", {
    namespace = var.namespace,
    datacenter = var.datacenter,
    dev = var.dev,
    config = var.config,
    interbroker_port = var.interbroker_port
  })
}

data "consul_service_health" "kafka_health" {
  count = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  name = "kafka-${count.index}"
  passing = true
  depends_on = [nomad_job.kafka]
  wait_for = "300s"
}

resource "consul_node" "self" {
  name = "self"
  address = "https://${var.consul_address}:8501"
}

resource "consul_service" "kafka-nginx" {
  count   = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  name    = "kafka-${count.index}"
  node    = "${consul_node.self.name}"
  tags    = ["nginx"]
  address = "nginx.service.consul"
  port    = 8080
}

