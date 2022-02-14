variable "feature_flags" {
  description = "A map specifying which feature flags to enable"
  type = map(bool)
  default = {}
}

variable "namespace" {
  description = "Job namespace"
  type = string
  default = ""
}

variable "consul_address" {
  description = "Dns address/resolved remote ip of consul"
  type = string
  default = "consul.service.consul"
}

variable "nomad_address" {
  description = "Dns address/resolved remote ip of nomad"
  type = string
  default = "nomad.service.consul"
}

variable "vault_address" {
  description = "Dns address/resolved remote ip of nomad"
  type = string
  default = "vault.service.consul"
}

variable "vault_token" {
  description = "Token used for communication with vault"
  type = string
  default = ""
}

variable "acl_token" {
  description = "Token used for communication with nomad and consul"
  type = string
  default = ""
}

variable "tls_ca_file" {
  description = "Path to the CA file used for TLS communication with vault, consul, and nomad"
  type = string
  default = ""
}

variable "tls_cert_file" {
  description = "Path to the certificate file used for TLS communication with vault and consul"
  type = string
  default = ""
}

variable "tls_key_file" {
  description = "Path to the key file used for TLS communication with vault and consul"
  type = string
  default = ""
}

variable "tls_nomad_cert_file" {
  description = "Path to the certificate file used for TLS communication with nomad"
  type = string
  default = ""
}

variable "tls_nomad_key_file" {
  description = "Path to the key file used for TLS communication with nomad"
  type = string
  default = ""
}
