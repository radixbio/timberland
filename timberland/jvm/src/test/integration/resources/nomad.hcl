server {
  enabled = true
  bootstrap_expect = 1
}
data_dir = "/tmp/nomad"

bind_addr = "{{ GetInterfaceIP \"ethwe0\" }}"
//bind_addr = "0.0.0.0"
client {
  enabled = true
  network_interface = "lo"
  alloc_dir = "/tmp/nomad/alloc"
  state_dir = "/tmp/nomad/state"

}

consul {
  address = "consul:8500"

  verify_ssl = false
}

advertise {
  http ="{{ GetInterfaceIP \"ethwe0\" }}"
  rpc = "{{ GetInterfaceIP \"ethwe0\" }}"
  serf = "{{ GetInterfaceIP \"ethwe0\" }}"
}

addresses {
  http ="{{ GetInterfaceIP \"ethwe0\" }}"
  rpc = "{{ GetInterfaceIP \"ethwe0\" }}"
  serf = "{{ GetInterfaceIP \"ethwe0\" }}"
}

plugin "docker" {
  config {
    allow_privileged = true
    allow_caps = ["ALL"]
    auth {
      config = "/root/config.json"
    }
    gc {
      image = false
    }
  }
}