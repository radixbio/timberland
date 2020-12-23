output "elasticsearch_health_result" {
  value = [for entry in data.consul_service_health.es_health: format("%s:%s", entry.results[0].service[0].address, entry.results[0].service[0].port)]
  depends_on = [data.consul_service_health.es_health]
}

output "kibana_health_result" {
  value = [for entry in data.consul_service_health.kibana_health: format("%s:%s", entry.results[0].service[0].address, entry.results[0].service[0].port)]
  depends_on = [data.consul_service_health.kibana_health]
}