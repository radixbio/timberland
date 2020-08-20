{{ with $ip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}
{{ with secret "pki_int/issue/tls-cert" "common_name=server.global.nomad" "ttl=24h" "alt_names=localhost,nomad.service.consul" (printf "ip_sans=127.0.0.1,%s" $ip)}}
{{ .Data.private_key }}
{{ end }}
{{ end }}