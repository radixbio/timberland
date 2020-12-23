variable "prefix" {
  description = "Job name prefix"
  type = string
  default = ""
}

variable "dev" {
  description = "Whether the runtime is being launched in dev mode"
  type = bool
  default = true //false
}
