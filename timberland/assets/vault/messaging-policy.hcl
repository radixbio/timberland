# Allow for enabling oauth plugin
path "sys/mounts/oauth2/messaging-bots" {
  capabilities = ["list", "read", "create", "update", "delete"]
}

# Allow reading/writing oath creds
path "oauth2/messaging-bots/creds/*" {
  capabilities = ["list", "read", "create", "update", "delete"]
}
path "oauth2/messaging-bots/servers/*" {
  capabilities = ["list", "read", "create", "update", "delete"]
}

# Allow reading/writing creds via secrets
path "secret/messaging-bots/*" {
  capabilities = ["list", "read", "create", "update", "delete"]
}
