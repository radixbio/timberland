package com.radix.utils.helm.http4s

object util {
  def getTokenFromEnvVars(): Option[String] = {
    val consulHttpToken = sys.env.get("CONSUL_HTTP_TOKEN")
    consulHttpToken match {
      case None => sys.env.get("NOMAD_TOKEN")
      case _ => consulHttpToken
    }
  }
}
