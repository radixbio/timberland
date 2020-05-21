{{- range service (printf "%s-discovery|passing" (env "NOMAD_GROUP_NAME")) }}
{{ .Address }}:{{ .Port }}{{ end }}
