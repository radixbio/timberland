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

variable "elasticsearch_quorum_size" {
  description = ""
  type = number
  default = 1
}

variable "algs_quorum_size" {
  type = number
  default = 1
}

variable "runtime_quorum_size" {
  type = number
  default = 1
}

variable "rainbow_quorum_size" {
  type = number
  default = 1
}

variable "scheduler_quorum_size" {
  type = number
  default = 1
}

variable "s3lts_quorum_size" {
  type = number
  default = 1
}

variable "namespace" {
  description = "Job namespace"
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
  default = "vault.service.consul"
}

variable "vault_token" {
  description = "Token used for communication with vault"
  type = string
  default = ""
}

variable "acl_token" {
  description = "Token used for communication with nomad and consul"
  type = string
  default = ""
}

variable "tls_ca_file" {
  description = "Path to the CA file used for TLS communication with vault, consul, and nomad"
  type = string
  default = ""
}

variable "tls_cert_file" {
  description = "Path to the certificate file used for TLS communication with vault and consul"
  type = string
  default = ""
}

variable "tls_key_file" {
  description = "Path to the key file used for TLS communication with vault and consul"
  type = string
  default = ""
}

variable "tls_nomad_cert_file" {
  description = "Path to the certificate file used for TLS communication with nomad"
  type = string
  default = ""
}

variable "tls_nomad_key_file" {
  description = "Path to the key file used for TLS communication with nomad"
  type = string
  default = ""
}

variable "nexus_username" {
  description = "Nexus package repo username"
  type = string
  default = null
}

variable "nexus_password" {
  description = "Nexus package repo password"
  type = string
  default = null
}
