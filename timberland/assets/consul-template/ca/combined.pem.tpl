{{ with secret "pki/cert/ca" }}
{{ if .Data.certificate }}
{{ .Data.certificate }}{{ end }}
{{ end }}
{{ with secret "pki_int/issue/tls-cert" "common_name=nomad.service.consul" "ttl=24h"}}
{{ .Data.issuing_ca }}
{{ end }}
