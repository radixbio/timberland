variable "have_upstream_creds" {
  description = "Upstream credentials exist in Vault, multiple minios should be spawned."
  type = bool
  default = false
}

variable "aws_access_key" {
  description = "Upstream AWS access key"
  type = string
  default = null
}

variable "aws_secret_key" {
  description = "Upstream AWS secret key"
  type = string
  default = null
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
