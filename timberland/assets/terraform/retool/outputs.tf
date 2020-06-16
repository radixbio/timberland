output "retool_health_result" {
  value = [for entry in data.consul_service_health.retool_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.retool_health]
}

output "postgres_health_result" {
  value = [for entry in data.consul_service_health.postgres_health: format("%s:%s", entry.results.0.service.0.address, entry.results.0.service.0.port)]
  depends_on = [data.consul_service_health.postgres_health]
}