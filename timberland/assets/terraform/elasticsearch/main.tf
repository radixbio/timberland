resource "nomad_job" "elasticsearch" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/elasticsearch/elasticsearch.tmpl", {
    config = var.config,
    dev = var.dev,
    datacenter = var.datacenter,
    namespace = var.namespace
  })
}

resource "nomad_job" "kibana" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/modules/elasticsearch/kibana.tmpl", {
    namespace = var.namespace,
    datacenter = var.datacenter,
    dev = var.dev,
    config = var.config
  })
}

data "consul_service_health" "es_health" {
  count = var.enable ? (var.dev ? 1 : var.config.quorum_size) : 0
  //TODO rest or transport?
  name = "es-transport-${count.index}"
  passing = true
  depends_on = [nomad_job.elasticsearch]
  wait_for = "300s"
}

data "consul_service_health" "kibana_health" {
  count = var.enable ? 1 : 0
  name = "kibana"
  passing = true
  depends_on = [nomad_job.kibana]
  wait_for = "300s"
}