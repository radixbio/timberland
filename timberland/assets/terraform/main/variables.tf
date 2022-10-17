variable "defined_config_vars" {
  description = "A list of defined config parameters in the form flagName.configName (e.g. minio.aws_access_key_id)"
  type = list(string)
  default = []
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
  description = "Dns address/resolved remote ip of consul"
  type = string
  default = "consul.service.consul"
}

variable "nomad_address" {
  description = "Dns address/resolved remote ip of nomad"
  type = string
  default = "nomad.service.consul"
}

variable "vault_address" {
  description = "Dns address/resolved remote ip of nomad"
  type = string
  default = "nomad.service.consul"
}

variable "acl_token" {
  description = "Token used for communication with nomad and consul"
  type = string
  default = ""
}
