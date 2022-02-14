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
