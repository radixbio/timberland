{{ $dc := env "DATACENTER" }}{{ $privip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}{{ $pubip := file "/opt/radix/timberland/.publicip"  }}
{{ with secret "pki_int/issue/tls-cert" "common_name=server.global.nomad" "ttl=24h" "alt_names=localhost,nomad.service.consul" (printf "ip_sans=127.0.0.1,%s,%s" $privip $pubip)}}
{{ .Data.certificate }}
{{ end }}
