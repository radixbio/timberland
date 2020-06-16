variable "test" {
  description = "Whether the runtime is being launched inside an integration test"
  type = bool
  default = false
}

variable "prefix" {
  description = "Job name prefix"
  type = string
  default = ""
}

variable "schema_registry_address" {
  type = list(string)
  default = []
}

variable "yb_tserver_address" {
  type = list(string)
  default = []
}

variable "connect_address" {
  type = list(string)
  default = []
}

variable "enable" {
  description = "Whether or not to create all the resources in this module"
  type = bool
  default = true
}
