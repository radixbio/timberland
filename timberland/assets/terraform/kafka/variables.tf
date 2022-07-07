variable "namespace" {
  description = "Job namespace"
  type = string
  default = ""
}

variable "enable" {
  description = "Whether or not to create all the resources in this module"
  type        = bool
  default     = true
}

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}

variable "dependencies" {
  description = "List of modules that this one depends on"
  type = list(string)
  default = ["zookeeper"]
}

variable "interbroker_port" {
  description = ""
  type = number
  default = 29092
}

variable "config" {
  type = object({
    quorum_size = number
  })
  description = ""
  default = {
    quorum_size = 3
  }
}

variable "consul_address" {
  description = "The ip or hostname of consul"
  type = string
  default = null
}
