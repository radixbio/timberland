path "secret/data/aws/*" {
  capabilities = ["list", "read", "update", "delete"]
}
path "secret/aws/*" {
  capabilities = ["list", "read", "update", "delete"]
}

