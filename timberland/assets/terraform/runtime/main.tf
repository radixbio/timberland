resource "nomad_job" "runtime" {
  count = var.enable ? 3 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/runtime/runtime.tmpl", {prefix = var.prefix, test = var.test})
}