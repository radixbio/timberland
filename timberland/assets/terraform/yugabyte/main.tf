resource "nomad_job" "yugabyte" {
  count = var.enable? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/yugabyte/yugabyte.tmpl", {quorum_size = var.quorum_size, test = var.test, prefix = var.prefix, dev = var.dev})
}

data "consul_service_health" "yb_master_health" {
  count = var.enable ? 1 : 0
  name = "yugabyte-yugabyte-ybmaster"
  passing = true
  depends_on = [nomad_job.yugabyte]
  wait_for = "300s"
}

data "consul_service_health" "yb_tserver_health" {
  count = var.enable ? 1 : 0
  name = "yugabyte-yugabyte-ybtserver"
  passing = true
  depends_on = [nomad_job.yugabyte]
  wait_for = "300s"
}