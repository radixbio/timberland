variable "namespace" {
  description = "Job namespace"
  type = string
  default = ""
}

variable "datacenter" {
  description = "Name of the datacenter for this nomad job"
  type = string
  default = ""
}

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}

variable "enable" {
  description = "Whether or not to create all the resources in this module"
  type = bool
  default = true
}
