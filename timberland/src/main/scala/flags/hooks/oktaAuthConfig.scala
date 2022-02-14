package com.radix.timberland.flags.hooks

import cats.effect.IO
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}

object oktaAuthConfig extends FlagHook {
  def run(options: Map[String, String], _addrs: ServiceAddrs, tokens: AuthTokens): IO[Unit] = {
    val vault = RadPath.runtime / "timberland" / "vault" / "vault"
    val caPath = RadPath.runtime / "certs" / "ca" / "cert.pem"
    val baseUrl = options("base_url")
    val orgName = options("org_name")
    val apiToken = options("api_token")
    val env = Map("VAULT_TOKEN" -> tokens.vaultToken, "VAULT_CACERT" -> caPath.toString)
    for {
      _ <- Util.exec(s"$vault auth enable okta", env = env)
      configCmd = s"$vault write auth/okta/config base_url=$baseUrl org_name=$orgName api_token=$apiToken"
      _ <- Util.exec(configCmd, env = env)
    } yield ()
  }
}
