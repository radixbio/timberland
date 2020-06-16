variable "upstream_access_key" {
  description = "The upstream access key for minio"
  type = string
  default = "minio-access-key"
}

variable "upstream_secret_key" {
  description = "The secret key for minio"
  type = string
  default = "minio-secret-key"
}

variable "prefix" {
  description = "Job name prefix"
  type = string
  default = ""
}

variable "test" {
  description = "Whether the runtime is being launched inside an integration test"
  type = bool
  default = false
}

variable "enable" {
  description = "Whether or not to create all the resources in this module"
  type = bool
  default = true
}

variable "kafka_address" {
  type = list(string)
  default = []
}