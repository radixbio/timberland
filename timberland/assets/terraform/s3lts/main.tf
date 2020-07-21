resource "nomad_job" "s3lts" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/s3lts/s3lts.tmpl", {prefix = var.prefix, test = var.test, runtime_address = var.runtime_address, schema_reg_addr = var.schema_registry_address, minio_address = var.minio_address})
}