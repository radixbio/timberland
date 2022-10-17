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
  memory_total_mb = 65535

  # by default, nomad will use envoy 1.11.2, which is not compatible with consul 1.9
  # https://www.consul.io/docs/connect/proxies/envoy.html#supported-versions
  # in nomad 1.0, nomad will automatically select the latest version that consul supports
  # https://github.com/hashicorp/nomad/pull/8945
  meta {
    connect.sidecar_image = "envoyproxy/envoy:v1.16.0@sha256:9e72bbba48041223ccf79ba81754b1bd84a67c6a1db8a9dbff77ea6fc1cb04ea"
  }

  options {
    docker.privileged.enabled = "true"
    docker.volumes.enabled = "true"
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

  host_volume "elasticsearch_data" {
    path = "/opt/radix/elasticsearch_data"
    read_only = false
  }

  host_volume "device_drivers" {
    path = "/opt/radix/device_drivers"
    read_only = false
  }

  chroot_env {
    "/bin" = "/bin",
    "/opt/radix/timberland/resolv.conf" = "/etc/resolv.conf",
    "/etc/ld.so.cache" = "/etc/ld.so.cache",
    "/etc/ld.so.conf" = "/etc/ld.so.conf",
    "/etc/ld.so.conf.d" = "/etc/ld.so.conf.d",
    "/etc/passwd" = "/etc/passwd",
    "/etc/sudoers" = "/etc/sudoers",
    "/etc/hosts" = "/etc/hosts",
    "/etc/java-11-openjdk" = "/etc/java-11-openjdk",
    "/lib" = "/lib",
    "/lib32" = "/lib32",
    "/lib64" = "/lib64",
    "/sbin" = "/sbin",
    "/usr" = "/usr",
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
    volumes {
      enabled = true
    }
  }
}

plugin "java" {
}

consul {
  address   = "localhost:8501"
  ssl       = true
  ca_file   = "/opt/radix/certs/ca/combined.pem"
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
