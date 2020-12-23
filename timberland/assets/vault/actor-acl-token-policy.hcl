# Allow looking up the vault acl token
path "secret/tokens/actor-token" {
  capabilities = ["read"]
}
