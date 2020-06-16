output "apprise_health_result" {
  value = [data.consul_service_health.apprise_health.*.results]
}