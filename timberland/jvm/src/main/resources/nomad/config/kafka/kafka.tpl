{{ range $tag, $services := service "radix-daemons-kafka-zookeeper|any" | byTag }}
{{ if $tag | contains "client" }}
{{ range $services }}
{{ scratch.MapSet .Address "client" .Port }}
{{ scratch.MapSet .Address "done" "false" }}
{{ end }}
{{ else }}
{{ end }}
{{ end }}
{{ scratch.Set "KAFKA_ZOOKEEPER_CONNECT" "" }}
{{ range $i, $services := service "radix-daemons-kafka-zookeeper|any" }}
{{ if ($services.Tags | contains "zookeeper-quorum") }}
{{ $addr := (scratch.Get .Address)}}
{{ if not (index $addr "done" | parseBool) }}
{{ $mynode := "temp" }}
{{ scratch.Set "KAFKA_ZOOKEEPER_CONNECT" (print (scratch.Get "KAFKA_ZOOKEEPER_CONNECT") $services.Address ":" (index $addr "client") "," )}}
{{ scratch.MapSet $services.Address "done" "true"}}
{{ end }}
{{ end }}
{{ end }}
{{ scratch.Get "KAFKA_ZOOKEEPER_CONNECT" }}
