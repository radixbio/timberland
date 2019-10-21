data_dir = "/tmp/radix/nomad"

server {
  enabled          = true
  bootstrap_expect = 3
}

client {
  enabled       = true
  network_speed = 1000
}

plugin "raw_exec" {
  config {
    enabled = true
  }
}

plugin "docker" {
  config {
    auth {
      config = "/root/.docker/config.json"
    }
  }
}

consul {
  address = "localhost:8500"
}
