output "have_upstream_creds" {
  value = local.have_upstream_creds
}

output "aws_access_key" {
  value = local.have_upstream_creds ? data.vault_aws_access_credentials.creds[0].access_key : null
}

output "aws_secret_key" {
  value = local.have_upstream_creds ? data.vault_aws_access_credentials.creds[0].secret_key : null
}