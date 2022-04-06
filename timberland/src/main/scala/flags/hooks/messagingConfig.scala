package com.radix.timberland.flags.hooks

import cats.effect.{ContextShift, IO, Sync}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{RadPath, Util}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{RegisterPluginRequest, Secret}
import com.radix.utils.tls.ConsulVaultSSLContext._
import org.http4s.Uri

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.global

object messagingConfig extends FlagHook {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def run(options: Map[String, String], serviceAddrs: ServiceAddrs, tokens: AuthTokens): IO[Unit] = {
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    val vault = new Vault[IO](authToken = Some(tokens.vaultToken), baseUrl = vaultUri)
    val pluginBinaryName = "vault-plugin-secrets-oauthapp"
    val pluginBinary = RadPath.persistentDir.toNIO.resolve("vault").resolve(pluginBinaryName)

    for {
      // compute the hash of the plugin binary. vault uses this as a security measure when registering plugins
      pluginHash <- Util.hashFile[IO](pluginBinary)

      // register the plugin (note: this does not enable the plugin)
      registerRequest = RegisterPluginRequest(pluginHash, pluginBinaryName)
      registrationResult <- vault.registerPlugin(Secret(), "oauthapp", registerRequest)

      // log the results
      _ <- registrationResult.left.toOption.map { error =>
        IO {
          scribe.error(s"Error registering oauth plugin: ${error.getMessage}")
        }
      }.getOrElse {
        IO {
          scribe.info("oauth plugin registered")
        }
      }
    } yield ()
  }
}
