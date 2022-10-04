# Allow reading rusers
path "identity/entity/id" {
  capabilities = ["list", "read"]
}
path "identity/entity/id/*" {
  capabilities = ["list", "read"]
}
