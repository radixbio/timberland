{{ with secret "pki_int/issue/tls-cert" "ttl=24h" }}
{{ .Data.private_key }}
{{ end }}
