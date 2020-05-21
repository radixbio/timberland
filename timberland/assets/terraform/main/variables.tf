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

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}

variable "launch_apprise" {
  description = "Whether the apprise job should be submitted"
  type = bool
  default = true
}

variable "launch_es" {
  description = "Whether the elasticsearch job should be submitted"
  type = bool
  default = false
}

variable "launch_elemental" {
  description = "Whether the elemental job should be submitted"
  type = bool
  default = true
}

variable "launch_es_kafka_connector" {
  description = "Whether the elasticsearch/kafka connector job should be submitted"
  type = bool
  default = true
}

variable "launch_kafka" {
  description = "Whether the kafka job should be submitted"
  type = bool
  default = true
}

variable "launch_kafka_companions" {
  description = "Whether the kafka companions job should be submitted"
  type = bool
  default = true
}

variable "launch_minio" {
  description = "Whether the minio job should be submitted"
  type = bool
  default = true
}

variable "launch_retool" {
  description = "Whether the retool job should be submitted"
  type = bool
  default = true
}

variable "launch_vault" {
  description = "Whether the vault job should be submitted"
  type = bool
  default = true
}

variable "launch_yugabyte" {
  description = "Whether the yugabyte head node job should be submitted"
  type = bool
  default = true
}

variable "launch_zookeeper" {
  description = "Whether the zookeeper job should be submitted"
  type = bool
  default = true
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