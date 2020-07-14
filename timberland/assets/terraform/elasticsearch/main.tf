resource "nomad_job" "elasticsearch" {
  count = var.enable ? 1 : 0
  jobspec = templatefile("/opt/radix/timberland/terraform/elasticsearch/elastic_search.tmpl", {quorum_size = var.quorum_size, dev = var.dev, prefix = var.prefix, test = var.test})
}

data "consul_service_health" "es_health" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}elasticsearch-es-es-generic-node"
  passing = true
  depends_on = [nomad_job.elasticsearch]
  wait_for = "300s"
}

data "consul_service_health" "kibana_health" {
  count = var.enable ? 1 : 0
  name = "${var.prefix}elasticsearch-kibana-kibana"
  passing = true
  depends_on = [nomad_job.elasticsearch]
  wait_for = "300s"
}