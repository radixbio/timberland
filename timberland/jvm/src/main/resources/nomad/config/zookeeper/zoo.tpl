{{ range $tag, $services := service "radix-daemons-zookeeper-zookeeper|any" | byTag }}
{{ if $tag | contains "client" }}
{{ range $services }}
{{ scratch.MapSet .Address "client" .Port }}
{{ scratch.MapSet .Address "done" "false" }}
{{ end }}
{{ else }}
{{ if $tag | contains "follower" }}
{{ range $services }}
{{ scratch.MapSet .Address "follower" .Port }}
{{ scratch.MapSet .Address "done" "false" }}
{{ end }}
{{ else }}
{{ if $tag | contains "othersrvs" }}
{{ range $services }}
{{ scratch.MapSet .Address "othersrvs" .Port }}
{{ scratch.MapSet .Address "done" "false" }}
{{ end }}
{{ else }}
{{ end }}
{{ end }}
{{ end }}
{{ end }}
{{ scratch.Set "index" 1 }}
{{ scratch.Set "ZOO_SERVERS" "" }}
{{ range $i, $services := service "radix-daemons-zookeeper-zookeeper|any" }}
{{ if ($services.Tags | contains "zookeeper-quorum") }}
{{ $addr := (scratch.Get .Address)}}
{{ if not (index $addr "done" | parseBool) }}
{{ $mynode := "temp" }}
{{ with node }}
{{ if eq $services.Node .Node.Node }}
{{ scratch.Set "ZOO_SERVERS" (print (scratch.Get "ZOO_SERVERS") "server." (scratch.Get "index") "=" $services.Address ":2888:3888\n" )}}
{{ scratch.Set "ZOO_MY_ID" (scratch.Get "index") }}
{{ else }}
{{ scratch.Set "ZOO_SERVERS" (print (scratch.Get "ZOO_SERVERS") "server." (scratch.Get "index") "=" $services.Address ":" (index $addr "follower") ":" ( index $addr "othersrvs") "\n" )}}
{{ end }}
{{ scratch.MapSet $services.Address "done" "true"}}
{{ scratch.Set "index" (scratch.Get "index" | add 1 ) }}
{{ end }}
{{ end }}
{{ end }}
{{ end }}
{{ scratch.Get "ZOO_SERVERS" }}
