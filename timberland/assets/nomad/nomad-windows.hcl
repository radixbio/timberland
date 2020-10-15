data_dir = "C:/opt/radix/nomad"
log_file = "C:/opt/radix/nomad/nomad.log"
vault {
  enabled     = true
  address     = "https://vault.service.consul:8200"
  ca_file     = "/opt/radix/certs/ca/cert.pem"
  cert_file   = "/opt/radix/certs/cli/cert.pem"
  key_file    = "/opt/radix/certs/cli/key.pem"
}

client {
  enabled         = true
  network_speed   = 1000
  options {
    docker.privileged.enabled = "true"
  }

}

plugin "raw_exec" {
  config {
    enabled = true
  }
}

plugin "java" {
}

consul {
  address   = "localhost:8501"
  ssl       = true
  ca_file   = "/opt/radix/certs/ca/cert.pem"
  cert_file = "/opt/radix/certs/cli/cert.pem"
  key_file  = "/opt/radix/certs/cli/key.pem"
}

acl {
  enabled = true
}

tls {
  http = true
  rpc  = true

  ca_file = "/opt/radix/certs/ca/cert.pem"
  cert_file = "/opt/radix/certs/nomad/cert.pem"
  key_file = "/opt/radix/certs/nomad/key.pem"

  verify_server_hostname = true
  verify_https_client    = false
}
