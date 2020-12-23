output "zookeeper_health_result" {
  value = [for entry in data.consul_service_health.zookeeper_health: format("%s:%s", entry.results[0].service[0].address, entry.results[0].service[0].port)]
  depends_on = [data.consul_service_health.zookeeper_health]
}