package com.radix.timberland.flags.hooks

import cats.effect.{ContextShift, IO}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}

import scala.concurrent.ExecutionContext
import scala.util.Random

object awsAuthConfig extends FlagHook {

  private val ec = ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  override def run(options: Map[String, String], addrs: ServiceAddrs, tokens: AuthTokens): IO[Unit] = {
    val accessKey = Random.alphanumeric.take(20).toList.mkString
    val secretKey = Random.alphanumeric.take(20).toList.mkString
    val vault = RadPath.runtime / "timberland" / "vault" / "vault"
    val caPath = RadPath.runtime / "certs" / "ca" / "cert.pem"
    val env = Map("VAULT_TOKEN" -> tokens.vaultToken, "VAULT_CACERT" -> caPath.toString)
    val minioKeysCmd = s"$vault kv put -cas=0 secret/minio-creds access_key=$accessKey secret_key=$secretKey"
    Util.exec(minioKeysCmd, env = env).map(_ => ())
  }
}
