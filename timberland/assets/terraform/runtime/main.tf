resource "nomad_job" "runtime" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/runtime/runtime.tmpl", {prefix = var.prefix, test = var.test})
}

data "consul_service_health" "runtime_health_result" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}runtime-bootstrap-discovery-0"
  passing = true
  depends_on = [nomad_job.runtime]
  wait_for = "300s"
}
