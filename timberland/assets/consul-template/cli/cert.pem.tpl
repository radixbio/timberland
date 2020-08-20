{{ with $ip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}
{{ with secret "pki_int/issue/tls-cert" "common_name=cli.dc1.consul" "ttl=24h" "alt_names=localhost" (printf "ip_sans=127.0.0.1,%s" $ip) }}
{{ .Data.certificate }}
{{ end }}
{{ end }}