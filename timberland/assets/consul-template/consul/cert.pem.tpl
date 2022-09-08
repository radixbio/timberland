{{ $dc := env "DATACENTER" }}{{ $privip := sockaddr "GetPrivateIPs" | replaceAll " " ","  }}{{ $pubip := file "/opt/radix/timberland/.publicip"  }}
{{ with secret "pki_int/issue/tls-cert" (printf "common_name=server.%s.consul" $dc) "ttl=24h" (printf "alt_names=localhost,consul.service.consul,%s-leader.server.%s.consul" $dc $dc) (printf "ip_sans=127.0.0.1,%s,%s" $privip $pubip)}}
{{ .Data.certificate }}
{{ end }}