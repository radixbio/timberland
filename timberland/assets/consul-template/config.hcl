vault {
  address = "https://127.0.0.1:8200"
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
  source = "/opt/radix/timberland/consul-template/resolv.conf.tpl"
  destination = "/opt/radix/timberland/resolv.conf"
  error_on_missing_key = true
  perms = 0644
  backup = true
}

template {
  source = "/opt/radix/timberland/consul-template/ca/cert.pem.tpl"
  destination = "/opt/radix/certs/ca/cert.pem"
  error_on_missing_key = true
  perms = 0644
  backup = true
  command = "sh -c 'systemctl reload nomad consul vault || true'"
}

// on a leader node, the root ca for consul connect might not have been created yet. don't error if it doesn't exist
template {
  source = "/opt/radix/timberland/consul-template/ca/combined.pem.tpl"
  destination = "/opt/radix/certs/ca/combined.pem"
  error_on_missing_key = false
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload nomad || true'"
}

// consul-template starts before nomad, don't error if it's not running
template {
  source = "/opt/radix/timberland/consul-template/nomad/cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload nomad || true'"
}

// consul-template starts before nomad, don't error if it's not running
template {
  source = "/opt/radix/timberland/consul-template/nomad/key.pem.tpl"
  destination = "/opt/radix/certs/nomad/key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload nomad || true'"
}

// consul-template starts before consul, don't error if it's not running
template {
  source = "/opt/radix/timberland/consul-template/consul/cert.pem.tpl"
  destination = "/opt/radix/certs/consul/cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload consul || true'"
}

// consul-template starts before consul, don't error if it's not running
template {
  source = "/opt/radix/timberland/consul-template/consul/key.pem.tpl"
  destination = "/opt/radix/certs/consul/key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload consul || true'"
}

// this certificate is only used by terraform, so no reloading is necessary
template {
  source = "/opt/radix/timberland/consul-template/nomad/cli-cert.pem.tpl"
  destination = "/opt/radix/certs/nomad/cli-cert.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
}

// this certificate is only used by terraform, so no reloading is necessary
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
  command = "sh -c 'systemctl reload vault || true'"
}

template {
  source = "/opt/radix/timberland/consul-template/vault/key.pem.tpl"
  destination = "/opt/radix/certs/vault/key.pem"
  error_on_missing_key = true
  perms = 0600
  backup = true
  command = "sh -c 'systemctl reload vault || true'"
}

template {
  source = "/opt/radix/timberland/consul-template/cli/cert.pem.tpl"
  destination = "/opt/radix/certs/cli/cert.pem"
  error_on_missing_key = true
  perms = 0644
  backup = true
  command = "sh -c 'systemctl reload vault || true'"
}

template {
  source = "/opt/radix/timberland/consul-template/cli/key.pem.tpl"
  destination = "/opt/radix/certs/cli/key.pem"
  error_on_missing_key = true
  perms = 0644
  backup = true
  command = "sh -c 'systemctl reload vault || true'"
}

// Add template for vault token (consul-template doesn't support this, file an issue?)
