data_dir = "/opt/radix/nomad"

vault {
  enabled     = true
  address     = "http://vault.service.consul:8200"
  create_from_role = "nomad-cluster"
}

server {
  enabled          = true
  bootstrap_expect = 3
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
  address = "localhost:8500"
}


