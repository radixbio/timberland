data_dir = "/opt/radix/nomad"

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

  host_volume "zookeeper_data" {
    path = "/opt/radix/zookeeper_data"
    read_only = false
  }

  host_volume "kafka_data" {
    path = "/opt/radix/kafka_data"
    read_only = false
  }

  host_volume "ybmaster_data" {
    path = "/opt/radix/ybmaster_data"
    read_only = false
  }

  host_volume "ybtserver_data" {
    path = "/opt/radix/ybtserver_data"
    read_only = false
  }

  host_volume "device_drivers" {
    path = "/opt/radix/device_drivers"
    read_only = false
  }
}

plugin "raw_exec" {
  config {
    enabled = true
  }
}

plugin "docker" {
  config {
    allow_privileged = true
    allow_caps = ["ALL"]
    auth {
      config = "/root/.docker/config.json"
    }
    gc {
      image = false
    }
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
