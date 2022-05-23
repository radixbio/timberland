consul {
  ssl {
    enabled = true
    verify = false
    ca_cert = "/opt/radix/certs/ca/cert.pem"
    cert = "/opt/radix/certs/cli/cert.pem"
    key  = "/opt/radix/certs/cli/key.pem"
  }
}

vault {
  address = "https://vault.service.consul:8200"
  renew_token = false
  ssl {
    enabled = true
    verify = false
    ca_cert = "/opt/radix/certs/ca/cert.pem"
    cert = "/opt/radix/certs/vault/cert.pem"
    key  = "/opt/radix/certs/vault/key.pem"
  }
}

template {
  source = "/opt/radix/timberland/consul-template/ca/cert.pem.tpl"
  destination = "/opt/radix/certs/ca/cert.pem"
  error_on_missing_key = true
  command = "C:\\opt\\radix\\timberland\\consul-template\\refresh-all.bat"
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cert.pem"
  command = "C:\\opt\\radix\\timberland\\consul-template\\refresh-nomad.bat"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/key.pem.tpl"
  destination = "/opt/radix/certs/nomad/key.pem"
  command = "C:\\opt\\radix\\timberland\\consul-template\\refresh-nomad.bat"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/consul/cert.pem.tpl"
  destination = "/opt/radix/certs/consul/cert.pem"
  command = "C:\\opt\\radix\\timberland\\consul-template\\refresh-consul.bat"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/consul/key.pem.tpl"
  destination = "/opt/radix/certs/consul/key.pem"
  command = "C:\\opt\\radix\\timberland\\consul-template\\refresh-consul.bat"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cli-cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cli-cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/nomad/cli-key.pem.tpl"
  destination = "/opt/radix/certs/nomad/cli-key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

// vault isn't always present, don't error if it's not running
template {
  source = "/opt/radix/timberland/consul-template/vault/cert.pem.tpl"
  destination = "/opt/radix/certs/vault/cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/vault/key.pem.tpl"
  destination = "/opt/radix/certs/vault/key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/cli/cert.pem.tpl"
  destination = "/opt/radix/certs/cli/cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/cli/key.pem.tpl"
  destination = "/opt/radix/certs/cli/key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

// Add template for vault token
