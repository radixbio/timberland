consul {
  ssl {
    enabled = true
    verify = true
    ca_cert = "/opt/radix/certs/ca/cert.pem"
    cert = "/opt/radix/certs/cli/cert.pem"
    key  = "/opt/radix/certs/cli/key.pem"
  }
}

vault {
  address = "https://vault.service.consul:8200"
  ssl {
    enabled = true
    ca_cert = "/opt/radix/certs/ca/cert.pem"
    cert = "/opt/radix/certs/vault/cert.pem"
    key  = "/opt/radix/certs/vault/key.pem"
  }
}

template {
  source = "/opt/radix/timberland/consul-template/ca/cert.pem.tpl"
  destination = "/opt/radix/certs/ca/cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/key.pem.tpl"
  destination = "/opt/radix/certs/nomad/key.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/consul/cert.pem.tpl"
  destination = "/opt/radix/certs/consul/cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/consul/key.pem.tpl"
  destination = "/opt/radix/certs/consul/key.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cli-cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cli-cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cli-key.pem.tpl"
  destination = "/opt/radix/certs/nomad/cli-key.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/vault/cert.pem.tpl"
  destination = "/opt/radix/certs/vault/cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/vault/key.pem.tpl"
  destination = "/opt/radix/certs/vault/key.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/cli/cert.pem.tpl"
  destination = "/opt/radix/certs/cli/cert.pem"
  error_on_missing_key = true
  perms = 0600
}

template {
  source = "/opt/radix/timberland/consul-template/cli/key.pem.tpl"
  destination = "/opt/radix/certs/cli/key.pem"
  error_on_missing_key = true
  perms = 0600
}

// Add template for vault token
