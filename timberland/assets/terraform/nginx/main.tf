resource "nomad_job" "nginx" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/nginx/nginx.tmpl", {
    namespace = var.namespace,
    services = var.dev ? data.consul_services.svc_list.names : [
      # "kibana",
      # "ui",
      # "api"
    ]
  })
}

data "consul_service_health" "nginx_health" {
  count = var.enable ? 1 : 0
  name = "nginx"
  passing = true
  depends_on = [nomad_job.nginx]
  wait_for = "600s"
}

data "consul_services" "svc_list" {
}
