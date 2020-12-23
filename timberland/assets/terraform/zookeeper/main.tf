resource "nomad_job" "zookeeper" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/zookeeper/zookeeper.tmpl", {dev = var.dev, prefix = var.prefix, test = var.test, quorum_size = var.quorum_size})
}

data "consul_service_health" "zookeeper_health" {
  count = var.enable ? (var.dev ? 1 : var.quorum_size) : 0
  name = "zookeeper-client-${count.index}"
  passing = true
  depends_on = [nomad_job.zookeeper]
  wait_for = "300s"
}
