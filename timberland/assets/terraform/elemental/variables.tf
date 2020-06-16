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

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}

variable "quorum_size" {
  type = number
  default = 1
}

variable "vault_token" {
  description = ""
  type = string
  default = "token"
}

variable "kafka_address" {
  type = list(string)
  default = []
}

variable "schema_registry_address" {
  type = list(string)
  default = []
}