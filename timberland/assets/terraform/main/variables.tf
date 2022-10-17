variable "minio_upstream_access_key" {
  description = "The upstream access key for minio"
  type = string
  default = "minio-access-key"
}

variable "minio_upstream_secret_key" {
  description = "The secret key for minio"
  type = string
  default = "minio-secret-key"
}

variable "test" {
  description = "Whether the runtime is being launched inside an integration test"
  type = bool
  default = false
}

variable "feature_flags" {
  description = "A list of feature flags to enable"
  type = list(string)
  default = []
}

variable "kafka_quorum_size" {
  description = ""
  type = number
  default = 1 //3
}

variable "kafka_interbroker_port" {
  description = ""
  type = number
  default = 29092
}

variable "kafka_companions_quorum_size" {
  description = ""
  type = number
  default = 1 //3
}

variable "vault_quorum_size" {
  description = ""
  type = number
  default = 1
}

variable "zookeeper_quorum_size" {
  description = ""
  type = number
  default = 1 //3
}

variable "elemental_quorum_size" {
  description = ""
  type = number
  default = 1 //3
}

variable "elemental_vault_token" {
  description = ""
  type = string
  default = "token"
}

variable "yugabyte_quorum_size" {
  description = ""
  type = number
  default = 1 //3
}

variable "elastic_search_quorum_size" {
  description = ""
  type = number
  default = 1
}

variable "prefix" {
  description = "Job name prefix"
  type = string
  default = ""
}

variable "consul_address" {
  description = "Resolved ip of consul"
  type = string
  default = "consul.service.consul"
}

variable "nomad_address" {
  description = "Resolved ip of nomad"
  type = string
  default = "nomad.service.consul"
}