variable "test" {
  description = "Whether the runtime is being launched inside an integration test"
  type = bool
  default = false
}

variable "namespace" {
  description = "Job namespace"
  type = string
  default = ""
}

variable "enable" {
  description = "Whether or not to create all the resources in this module"
  type = bool
  default = true
}
