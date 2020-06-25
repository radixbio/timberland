resource "nomad_job" "minio" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/minio/minio.tmpl", {prefix = var.prefix, test = var.test, have_upstream_creds = var.have_upstream_creds, kafka_address = var.kafka_address})
}