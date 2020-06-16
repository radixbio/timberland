resource "nomad_job" "apprise" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/apprise/apprise.tmpl", {prefix = var.prefix, test = var.test})
}

data "consul_service_health" "apprise_health" {
  count = var.enable ? 1 : 0
  name = "apprise-apprise-apprise"
  passing = true
  depends_on = [nomad_job.apprise]
  wait_for = "300s"
}