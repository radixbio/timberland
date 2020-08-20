{{ with secret "pki_int/issue/tls-cert" "common_name=nomad.service.consul" "ttl=24h"}}
{{ .Data.issuing_ca }}
{{ end }}
