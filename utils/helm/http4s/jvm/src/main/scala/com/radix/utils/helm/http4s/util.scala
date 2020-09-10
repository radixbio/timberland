package com.radix.utils.helm.http4s

object util {
  def getTokenFromEnvVars(): Option[String] = {
    val consulHttpToken = sys.env.get("CONSUL_HTTP_TOKEN")
    consulHttpToken match {
      case None =>
        val consulHttpToken = System.getProperty("CONSUL_HTTP_TOKEN") //To allow for runtime overrides for develloping
        if (consulHttpToken == null) {
          sys.env.get("NOMAD_TOKEN")
        } else {
          Some(consulHttpToken)
        }
      case _ => consulHttpToken
    }
  }
}
