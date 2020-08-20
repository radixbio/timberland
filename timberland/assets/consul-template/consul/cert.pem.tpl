{{ with $ip := sockaddr "GetPrivateIPs" | replaceAll " " "," }}
{{ with secret "pki_int/issue/tls-cert" "common_name=server.dc1.consul" "ttl=24h" "alt_names=localhost,consul.service.consul" (printf "ip_sans=127.0.0.1,%s" $ip)}}
{{ .Data.certificate }}
{{ end }}
{{ end }}