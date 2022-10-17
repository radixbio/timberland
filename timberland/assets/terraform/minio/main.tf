resource "nomad_job" "minio" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/minio/minio.tmpl", {
    prefix = var.prefix,
    test = var.test,
    have_upstream_creds = var.have_upstream_creds,
    aws_access_key = var.aws_access_key,
    aws_secret_key = var.aws_secret_key,
  })
}

data "consul_service_health" "minio_health" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}minio-job-minio-group-minio-local"
  passing = true
  depends_on = [nomad_job.minio]
  wait_for = "300s"
}