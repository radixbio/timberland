{{ with $ip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}
{{ with secret "pki_int/issue/tls-cert" "common_name=vault.service.consul" "ttl=24h" "alt_names=localhost" (printf "ip_sans=127.0.0.1,%s" $ip)}}
{{ .Data.private_key }}
{{ end }}
{{ end }}