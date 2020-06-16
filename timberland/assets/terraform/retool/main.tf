resource "nomad_job" "retool" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/retool/retool.tmpl", {prefix = var.prefix, test = var.test, })
}

data "consul_service_health" "retool_health" {
  count = var.enable ? 1 : 0
  name = "retool-retool-retool-main"
  passing = true
  depends_on = [nomad_job.retool]
  wait_for = "300s"
}

data "consul_service_health" "postgres_health" {
  count = var.enable ? 1 : 0
  name = "retool-retool-postgres"
  passing = true
  depends_on = [nomad_job.retool]
  wait_for = "300s"
}