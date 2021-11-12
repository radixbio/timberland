#!/usr/bin/env bash

# Produce an easy-to-read summary of results in CI, including
# - whether Vault is sealed
# - whether Consul thinks Vault is sealed. If Vault is unsealed but Consul thinks it is sealed, investigate Vault logs
# - x509 errors in consul logs
# - x509 errors in nomad logs (particularly ones from trying to talk to Consul's grpc socket)
# - x509 errors in vault logs
# - grpc config errors in envoy logs
vault_token=$(cat /opt/radix/timberland/.vault-token)

if [ -f /opt/radix/timberland/.acl-token ]; then
  consul_token=$(cat /opt/radix/timberland/.acl-token)
elif [ -f /opt/radix/timberland/.vault-token ]; then
  VAULT_RET=$(VAULT_TOKEN="$vault_token" VAULT_ADDR=https://vault.service.consul:8200 /opt/radix/timberland/vault/vault kv get secret/consul-ui-token)
  consul_token=${VAULT_RET##*:}
  consul_token=${TOKEN%%]}
else
  echo "Consul UI token not found. Some things might not work. oh well"
  echo ""
fi

vault_sealed=$(curl -H "X-Vault-Token: $vault_token" -ks https://127.0.0.1:8200/v1/sys/seal-status | jq '.sealed')
printf "Vault sealed: %s\n" "$vault_sealed"

consul_vault_check=$(curl -H "X-Consul-Token: $consul_token" -s consul.service.consul:8500/v1/health/checks/vault)
status=$(echo "$consul_vault_check" | jq -r '.[0].Status')
output=$(echo "$consul_vault_check" | jq -r '.[0].Output')
printf "Consul vault check: %s, %s\n" "$status" "$output"

if [ "$vault_sealed" == "false" ] && [ "$output" == "Vault Sealed" ]; then
  echo "Vault is unsealed, but Consul thinks it's sealed. Investigating Vault logs."
  vault_svc_reg_res=$(journalctl -u vault | grep -m 1 "service registration failed" | sed -En 's/.*(\[WARN\].*)/\1/p')
  if [ "$vault_svc_reg_res" != "" ]; then
    printf "Found service registration error in vault logs:\n%s\n\n" "$vault_svc_reg_res"
  else
    echo "Didn't find service registration error in vault logs. Something very funky happened."
  fi
fi

echo ""
nomad_consul_grpc_err=$(journalctl -u nomad | grep -m 1 splice | sed -En 's/.*(\[WARN\].*)/\1/p')
if [ "$nomad_consul_grpc_err" != "" ]; then
  printf "Found x509 in Nomad logs when it tried to talk to the Consul grpc socket:\n%s\n\n" "$nomad_consul_grpc_err"
else
  echo "Didn't find any errors having to do with the Consul grpc socket in the Nomad logs."
fi

echo "Searching for x509 errors in Consul logs..."

consul_x509_errs=$(journalctl -u consul | grep x509)

proxycfg_errs=$(echo "$consul_x509_errs" | grep proxycfg | sed -n 's/.*error="//p' | sort -u | sed -n 's/^/  - /p')
if [ "$proxycfg_errs" != "" ]; then
  echo "- Found the following x509 proxcfg errors."
  echo "$proxycfg_errs"
  echo ""
else
  echo "Didn't find any proxycfg x509 errors in the Consul logs."
fi

not_proxycfg_errs=$(echo "$consul_x509_errs" | grep -v proxycfg | sed -n 's/.*error="//p' | sort -u | sed -n 's/^/  - /p')
if [ "$not_proxycfg_errs" != "" ]; then
  echo "- Found the following x509 non-proxycfg errors."
  echo "$not_proxycfg_errs"
  echo ""
else
  echo "Didn't find any non-proxycfg x509 errors in the Consul logs."
fi

if [ "$proxycfg_errs" == "" ] && [ "$not_proxycfg_errs" == "" ]; then
  echo "No x509 errors found in Consul logs. Yay!"
  echo ""
fi

nomad_x509_errs=$(journalctl -u nomad | grep x509 | sed -En 's/.*\[ERROR\] (.*)alloc_id=.*task=.*(error=.*)/\1\2/p' | sort -u | sed -n 's/^/  - /p')
if [ "$nomad_x509_errs" != "" ]; then
  echo "Found the following x509 errors in the Nomad logs."
  echo "$nomad_x509_errs"
  echo ""
else
  echo "Didn't find any x509 errors in the Nomad logs."
  echo ""
fi

vault_x509_errs=$(journalctl -u vault | grep x509 | grep -v "service_registration" | sed -n 's/^/  - /p')
#echo "$vault_x509_errs"

if [ "$vault_x509_errs" != "" ]; then
  echo "Found non-service-registration-related x509 errors in Vault logs."
  echo "$vault_x509_errs"
  echo ""
else
  echo "Didn't find any non-service-registration-related x509 errors in Vault logs."
  echo ""
fi

vault_tls_errs=$(journalctl -u vault | grep tls | sed -En 's/.*\[INFO\] (.*):[[:digit:]]+:(.*)/\1\2/p' | sort -u | sed -n 's/^/  - /p')
if [ "$vault_tls_errs" != "" ]; then
  echo "Found tls errors in Vault logs."
  echo "$vault_tls_errs"
  echo ""
else
  echo "Didn't find any tls errors in Vault logs."
  echo ""
fi
