resource "nomad_job" "yb_masters" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/yugabyte/yb_masters.tmpl", {namespace = var.namespace, config = var.config, dev = var.dev})
}

resource "nomad_job" "yb_tservers" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/yugabyte/yb_tservers.tmpl", {namespace = var.namespace, config = var.config, dev = var.dev})
}

data "consul_service_health" "yb_master_health" {
  count = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  name = "yb-masters-rpc-${count.index}"
  passing = true
  depends_on = [nomad_job.yb_masters]
  wait_for = "300s"
}

data "consul_service_health" "yb_tserver_health" {
  count = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  name = "yb-tserver-connect-${count.index}"
  passing = true
  depends_on = [nomad_job.yb_tservers]
  wait_for = "300s"
}
