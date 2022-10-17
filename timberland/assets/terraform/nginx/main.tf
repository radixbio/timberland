resource "nomad_job" "nginx" {
  count = 1
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/nginx/nginx.tmpl", {
    prefix = var.prefix,
    services = var.dev ? data.consul_services.svc_list.names : [
      "minio-remote-service",
      "minio-local-service",
      "messaging-slack-button-callback"
      # "kibana",
      # "ui",
      # "api"
    ]
  })
}

data "consul_service_health" "nginx_health" {
  count = 1
  name = "nginx"
  passing = true
  depends_on = [nomad_job.nginx]
  wait_for = "300s"
}

data "consul_services" "svc_list" {
}
