locals {
  have_upstream_creds = fileexists("${path.cwd}/.aws-creds") ? jsondecode(file("${path.cwd}/.aws-creds")).credsExistInVault : false
}

data "vault_aws_access_credentials" "creds" {
  count = local.have_upstream_creds ? 1 : 0
  backend = "aws"
  role    = "aws-cred"
}