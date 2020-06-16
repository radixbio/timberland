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

variable "quorum_size" {
  type = number
  default = 1
}

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}

variable "interbroker_port" {
  description = ""
  type = number
  default = 29092
}

variable "kafka_address" {
  type = list(string)
  default = []
}