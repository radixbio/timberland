data_dir = "/tmp/radix/nomad"
datacenter = "dc1"
server {
    enabled = true
    bootstrap_expect = 3
}
//This lack of end paren is intentional, it gets filled in from timberland's launch.
client {
    enabled = true
