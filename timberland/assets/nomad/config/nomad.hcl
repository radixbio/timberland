data_dir = "/opt/radix/nomad"

server {
  enabled          = true
  bootstrap_expect = 3
}

client {
  enabled       = true
  network_speed = 1000
  options {
      docker.privileged.enabled = "true"
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


