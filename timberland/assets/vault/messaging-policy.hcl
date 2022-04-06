path "secret/messaging_credentials_slack" {
  capabilities = ["list", "read", "create", "update", "delete"]
}

path "oauth2/messaging/creds/messaging_credentials_mteams" {
  capabilities = ["list", "read", "create", "update", "delete"]
}

path "oauth2/messaging/servers/messaging_credentials_mteams" {
  capabilities = ["list", "read", "create", "update", "delete"]
}

path "sys/mounts/oauth2/messaging" {
  capabilities = ["list", "read", "create", "update", "delete"]
}
