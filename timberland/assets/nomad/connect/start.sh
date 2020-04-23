#!/usr/bin/env ash

command="curl -X PUT \
http://kafka-companion-daemons-kafkaCompanions-kafkaConnect.service.consul:8083/connectors/elasticsearch-sink/config \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
-d '{
\"connector.class\": \"io.confluent.connect.elasticsearch.ElasticsearchSinkConnector\",
\"type.name\": \"radix\",
\"offset.flush.interval.ms\": \"1000\",
\"value.converter.schema.registry.url\": \"http://kafka-companion-daemons-kafkaCompanions-schemaRegistry.service.consul:8081/\",
\"topics.regex\": \".*-journal\",
\"tasks.max\": \"1\",
\"errors.tolerance\": \"all\",
\"connection.url\": \"http://elasticsearch-elasticsearch-es-generic-node.service.consul:9200\",
\"value.converter\": \"io.confluent.connect.avro.AvroConverter\",
\"key.converter\": \"org.apache.kafka.connect.storage.StringConverter\"
}'"

eval $command

while [ $? -ne 0 ]; do sleep 2; eval $command; done
