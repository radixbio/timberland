nameserver {{ with node }}{{ .Node.Address }}{{ end }}
nameserver 8.8.8.8
nameserver 8.8.4.4