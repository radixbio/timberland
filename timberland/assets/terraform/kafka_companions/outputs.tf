output "schema_registry_health_result" {
  value = [for entry in data.consul_service_health.schema_registry_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.schema_registry_health]
}

output "connect_health_result" {
  value = [for entry in data.consul_service_health.connect_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.connect_health]
}

output "rest_proxy_health_result" {
  value = [for entry in data.consul_service_health.rest_proxy_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.rest_proxy_health]
}

output "ksql_health_result" {
  value = [for entry in data.consul_service_health.ksql_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.ksql_health]
}