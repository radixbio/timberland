locals {
  have_upstream_creds = fileexists("${path.cwd}/.aws-creds") ? jsondecode(file("${path.cwd}/.aws-creds")).credsExistInVault : false
}

data "vault_aws_access_credentials" "creds" {
  count = local.have_upstream_creds ? 1 : 0
  backend = "aws"
  role    = "aws-cred"
}

data "vault_generic_secret" "minio_creds" {
  path = "secret/minio-creds"
}

resource "nomad_job" "minio" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/minio/minio.tmpl", {
    prefix = var.prefix,
    test = var.test,
    have_upstream_creds = local.have_upstream_creds,
    aws_access_key = local.have_upstream_creds ? data.vault_aws_access_credentials.creds[0].access_key : null,
    aws_secret_key = local.have_upstream_creds ? data.vault_aws_access_credentials.creds[0].secret_key : null,
    minio_access_key = data.vault_generic_secret.minio_creds.data["access_key"],
    minio_secret_key = data.vault_generic_secret.minio_creds.data["secret_key"],
  })
}

data "consul_service_health" "minio_local_health" {
  count = var.enable ? 1 : 0
  name = "minio-local-service"
  passing = true
  depends_on = [nomad_job.minio]
  wait_for = "300s"
}

data "consul_service_health" "minio_remote_health" {
  count = var.enable && var.have_upstream_creds ? 1 : 0
  name = "minio-remote-service"
  passing = true
  depends_on = [nomad_job.minio]
  wait_for = "300s"
}