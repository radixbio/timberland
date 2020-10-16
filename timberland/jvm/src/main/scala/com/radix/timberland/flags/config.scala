package com.radix.timberland.flags

import cats.effect.IO
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.RadPath

case object config {

  /**
   * Config entries can be defined here, organized by flag name
   */
  val flagConfigParams: Map[String, List[FlagConfigEntry]] = Map(
    "minio" -> List(
      FlagConfigEntry(
        key = "aws_access_key_id",
        isSensitive = true,
        prompt = "Minio AWS Access Key ID",
        optional = true
      ),
      FlagConfigEntry(
        key = "aws_secret_access_key",
        isSensitive = true,
        prompt = "Minio AWS Secret Access Key",
        optional = true
      )
    ),
    "elemental" -> List(
      FlagConfigEntry(
        key = "username",
        isSensitive = true,
        prompt = "Elemental Machines Username",
        optional = true
      ),
      FlagConfigEntry(
        key = "password",
        isSensitive = true,
        prompt = "Elemental Machines Password",
        optional = true
      )
    ),
    "google-oauth" -> List(
      FlagConfigEntry(
        key = "GOOGLE_OAUTH_ID",
        isSensitive = true,
        prompt = "Google OAUTH ID"
      ),
      FlagConfigEntry(
        key = "GOOGLE_OAUTH_SECRET",
        isSensitive = true,
        prompt = "Google OAUTH Secret"
      )
    ),
    "docker-auth" -> List(
      FlagConfigEntry(
        key = "ID",
        isSensitive = true,
        prompt = "Login ID for the container registry",
        optional = true,
        default = Some("radix-fetch-images")
      ),
      FlagConfigEntry(
        key = "TOKEN",
        isSensitive = true,
        prompt = "Password or token to the container registry",
        optional = true,
        // read-registry-only permissions, for all gitlab private projects, expires 2023-01-01
        // revoke @ https://gitlab.com/profile/personal_access_tokens
        default = Some("Mz-TpEuBXch9n65KZKZe")
      ),
      FlagConfigEntry(
        key = "URL",
        isSensitive = true,
        prompt = "URL of the container registry",
        optional = true,
        default = Some("registry.gitlab.com")
      )
    ),
    "custom_tag" -> List(
      FlagConfigEntry(
        key = "TAG",
        isSensitive = false,
        prompt = "Branch/tag of images to retrieve from Radix repository",
        optional = true,
        default = None,
        terraformVar = Some("custom_tag")
      )
    )
  )

  // Defines functions that get run for
  val flagConfigHooks: Map[String, List[(Map[String, Option[String]], ServiceAddrs) => IO[Unit]]] =
    Map(
      "google-oauth" -> List((new OauthConfig).handler),
      "docker-auth" -> List(daemonutil.handleRegistryAuth),
      "minio" -> List((_, _) =>
        for {
          _ <- IO.pure()
          minioFolders = List(
            RadPath.persistentDir / "nginx",
            RadPath.persistentDir / "minio_data",
            RadPath.persistentDir / "minio_data" / "userdata"
          )
          _ <- IO(minioFolders.map(path => os.makeDir.all(path)))
        } yield ()
      ),
      "ensure-supported" -> List((_, _) => daemonutil.ensureSupported())
    )

  // The list of flags that are enabled by default - "tui" only enabled by default on linux/amd64
  val flagDefaults
    : List[String] = (List("ensure-supported", "core", "dev", "docker-auth", "remote_images") ++ (if (daemonutil.osname == "linux" & daemonutil.arch == "amd64")
                                                                                                    List("tui")
                                                                                                  else List()))
}
