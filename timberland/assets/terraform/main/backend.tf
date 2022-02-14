terraform {
  backend "consul" {
    address   = "consul.service.consul:8501"
    scheme    = "https"
    ca_file   = "/opt/radix/certs/ca/cert.pem"
    cert_file = "/opt/radix/certs/cli/cert.pem"
    key_file  = "/opt/radix/certs/cli/key.pem"
    path      = "terraform"
  }
}
provider "consul" {
  address = "https://${var.consul_address}:8501"
  ca_file = var.tls_ca_file
  cert_file = var.tls_cert_file
  key_file = var.tls_key_file
  token = var.acl_token
}

provider "nomad" {
  address = "https://${var.nomad_address}:4646"
  ca_file = var.tls_ca_file
  cert_file = var.tls_nomad_cert_file
  key_file = var.tls_nomad_key_file
  secret_id = var.acl_token
}

provider "vault" {
  address = "https://${var.vault_address}:8200"
  ca_cert_file = var.tls_ca_file
  token = var.vault_token
}
