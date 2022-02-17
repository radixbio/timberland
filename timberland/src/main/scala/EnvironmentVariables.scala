package com.radix.timberland

import com.radix.timberland.util.RadPath

object EnvironmentVariables {
  val envVars = Map(
    "NOMAD_ADDR" -> "https://nomad.service.consul:4646",
    "NOMAD_CACERT" -> ConstPaths.certDir / "ca" / "cert.pem",
    "NOMAD_CLIENT_CERT" -> ConstPaths.certDir / "nomad" / "cli-cert.pem",
    "NOMAD_CLIENT_KEY" -> ConstPaths.certDir / "nomad" / "cli-key.pem",
    "CONSUL_HTTP_ADDR" -> "https://consul.service.consul:8501",
    "CONSUL_HTTP_SSL" -> "true",
    "CONSUL_CACERT" -> ConstPaths.certDir / "ca" / "cert.pem",
    "CONSUL_CLIENT_CERT" -> ConstPaths.certDir / "cli" / "cert.pem",
    "CONSUL_CLIENT_KEY" -> ConstPaths.certDir / "cli" / "key.pem",
    "VAULT_ADDR" -> "https://vault.service.consul:8200",
    "VAULT_CACERT" -> ConstPaths.certDir / "ca" / "cert.pem",
    "VAULT_CLIENT_CERT" -> ConstPaths.certDir / "cli" / "cert.pem",
    "VAULT_CLIENT_KEY" -> ConstPaths.certDir / "cli" / "key.pem",
    "VAULT_TOKEN" -> os.read(RadPath.persistentDir / ".vault-token"),
    "TF_VAR_TLS_CA_FILE" -> os.Path(sys.env.getOrElse("TLS_CA", (ConstPaths.certDir / "ca" / "cert.pem").toString)),
    "TF_VAR_TLS_CERT_FILE" -> os.Path(
      sys.env.getOrElse("TLS_CERT", (ConstPaths.certDir / "cli" / "cert.pem").toString)
    ),
    "TF_VAR_TLS_KEY_FILE" -> os.Path(sys.env.getOrElse("TLS_KEY", (ConstPaths.certDir / "cli" / "key.pem").toString)),
    "TF_VAR_TLS_NOMAD_CERT_FILE" -> os.Path(
      sys.env.getOrElse("TLS_NOMAD_CERT", (ConstPaths.certDir / "nomad" / "cli-cert.pem").toString)
    ),
    "TF_VAR_TLS_NOMAD_KEY_FILE" -> os.Path(
      sys.env.getOrElse("TLS_NOMAD_KEY", (ConstPaths.certDir / "nomad" / "cli-key.pem").toString)
    )
  )
  def envToken(token: String) = envVars ++ Map(
    "NOMAD_TOKEN" -> token,
    "CONSUL_HTTP_TOKEN" -> token,
    "TF_VAR_ACL_TOKEN" -> token
  )
}

object ConstPaths {
  val bootstrapComplete = RadPath.persistentDir / ".bootstrap-complete"

  val certDir: os.Path = RadPath.runtime / "certs"

  val persistentDir: os.Path = RadPath.runtime / "timberland"

  // this path has the terraform directory that timberland ships with...
  val execDir = RadPath.runtime / "timberland" / "terraform"
  // and this directory has the actual terraform working directory

  val workingDir = RadPath.runtime / "terraform"
  // this file controls which namespace is passed to terraform by default
  // this can be overridden with --namespace
  val namespaceFile = RadPath.runtime / "timberland" / "release-name.txt"

  val TF_MODULES_DIR: os.Path = RadPath.persistentDir / "terraform" / "modules"
  val TF_CONFIG_DIR: os.Path = RadPath.runtime / "config" / "modules"

}
