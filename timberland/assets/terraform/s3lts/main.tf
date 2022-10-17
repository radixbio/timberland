resource "nomad_job" "s3lts" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/s3lts/s3lts.tmpl", {prefix = var.prefix, test = var.test})
}