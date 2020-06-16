resource "nomad_job" "minio" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/minio/minio.tmpl", {prefix = var.prefix, test = var.test, upstream_access_key = var.upstream_access_key, upstream_secret_key = var.upstream_secret_key, kafka_address = var.kafka_address})
}