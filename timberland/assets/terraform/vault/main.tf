resource "nomad_job" "vault" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/vault/vault.tmpl", {prefix = var.prefix, test = var.test, quorum_size = var.quorum_size})
}
