{{ $privip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}{{ $pubip := file "/opt/radix/timberland/.publicip"  }}
{{ with secret "pki_int/issue/tls-cert" "common_name=vault.service.consul" "ttl=24h" "alt_names=localhost" (printf "ip_sans=127.0.0.1,%s,%s" $privip $pubip)}}
{{ .Data.private_key }}
{{ end }}