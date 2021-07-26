package com.radix.timberland.flags

import com.radix.timberland.flags.hooks.{awsAuthConfig, dockerAuthConfig, ensureSupported, oauthConfig, oktaAuthConfig, FlagHook}
import cats.effect.IO
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.RadPath

case object config {

  /**
   * Config entries can be defined here, organized by flag name
   */
  val flagConfigParams: Map[String, List[FlagConfigEntry]] = Map(
    "zookeeper" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Zookeeper quorum size",
        optional = true,
        default = Some("3"),
        terraformVar = Some("zookeeper_quorum_size")
      )
    ),
    "kafka" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Kafka quorum size",
        optional = true,
        default = Some("3"),
        terraformVar = Some("kafka_quorum_size")
      )
    ),
    "yugabyte" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Yugabyte quorum size",
        optional = true,
        default = Some("3"),
        terraformVar = Some("yugabyte_quorum_size")
      )
    ),
    "elasticsearch" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Elasticsearch quorum size",
        optional = true,
        default = Some("3"),
        terraformVar = Some("elasticsearch_quorum_size")
      )
    ),
    "runtime" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Runtime quorum size",
        optional = true,
        default = Some("3"),
        terraformVar = Some("runtime_quorum_size")
      )
    ),
    "minio" -> List(
      FlagConfigEntry(
        key = "aws_access_key_id",
        destination = Nowhere,
        prompt = "Minio AWS Access Key ID",
        optional = true
      ),
      FlagConfigEntry(
        key = "aws_secret_access_key",
        destination = Nowhere,
        prompt = "Minio AWS Secret Access Key",
        optional = true
      )
    ),
    "s3lts" -> List(
      FlagConfigEntry(
        key = "target_bucket_name",
        destination = Consul,
        prompt = "Remote Bucket Name",
        optional = true,
        default = Some("radix-userdata-test")
      )
    ),
    "rainbow" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Rainbow quorum size",
        optional = true,
        default = Some("1"),
        terraformVar = Some("rainbow_quorum_size")
      )
    ),
    "scheduler" -> List(
      FlagConfigEntry(
        key = "quorum_size",
        destination = Nowhere,
        prompt = "Scheduler quorum size",
        optional = true,
        default = Some("1"),
        terraformVar = Some("scheduler_quorum_size")
      )
    ),
    "elemental_bridge" -> List(
      FlagConfigEntry(
        key = "username",
        destination = Vault,
        prompt = "Elemental Machines Username",
        default = Some("elemental@radix.bio")
      ),
      FlagConfigEntry(
        key = "password",
        destination = Vault,
        prompt = "Elemental Machines Password",
        default = Some("Rad!xlabsens0rs")
      ),
      FlagConfigEntry(
        key = "client_id",
        destination = Vault,
        prompt = "Elemental Machines Client ID",
        default = Some("key-em-api-prod")
      ),
      FlagConfigEntry(
        key = "client_secret",
        destination = Vault,
        prompt = "Elemental Machines Secret Key",
        default = Some("secret-em-api-prod")
      )
    ),
    "osipi_connector" -> List(
      FlagConfigEntry(
        key = "url",
        destination = Vault,
        prompt = "OSIPi Server URL",
        default = Some("https://localhost:443/piwebapi/omf")
      ),
      FlagConfigEntry(
        key = "auth_method",
        destination = Vault,
        prompt = "OSIPi Auth Method [Bearer, Basic]",
        default = Some("Bearer")
      ),
      FlagConfigEntry(
        key = "bearer_token",
        destination = Vault,
        prompt = "OSIPi Bearer Token",
        optional = true
      ),
      FlagConfigEntry(
        key = "basic_username",
        destination = Vault,
        prompt = "OSIPi Username",
        optional = true
      ),
      FlagConfigEntry(
        key = "basic_password",
        destination = Vault,
        prompt = "OSIPi Password",
        optional = true
      )
    ),
    "google-oauth" -> List(
      FlagConfigEntry(
        key = "GOOGLE_OAUTH_ID",
        destination = Vault,
        prompt = "Google OAUTH ID"
      ),
      FlagConfigEntry(
        key = "GOOGLE_OAUTH_SECRET",
        destination = Vault,
        prompt = "Google OAUTH Secret"
      )
    ),
    "okta-auth" -> List(
      FlagConfigEntry(
        key = "base_url",
        destination = Nowhere,
        prompt = "Okta base url"
      ),
      FlagConfigEntry(
        key = "org_name",
        destination = Nowhere,
        prompt = "Okta organization name"
      ),
      FlagConfigEntry(
        key = "api_token",
        destination = Nowhere,
        prompt = "Okta API token"
      )
    ),
    "remote_images" -> List(
      FlagConfigEntry(
        key = "nexus_username",
        destination = Vault,
        prompt = "Nexus Repository Username",
        terraformVar = Some("nexus_username"),
        optional = true,
        default = Some("admin")
      ),
      FlagConfigEntry(
        key = "nexus_password",
        destination = Vault,
        prompt = "Nexus Repository Password",
        terraformVar = Some("nexus_password"),
        optional = true,
        default = Some("radix789")
      )
    ),
    "docker-auth" -> List(
      FlagConfigEntry(
        key = "ID",
        destination = Vault,
        prompt = "Login ID for the container registry",
        optional = true,
        default = Some("radix-fetch-images")
      ),
      FlagConfigEntry(
        key = "TOKEN",
        destination = Vault,
        prompt = "Password or token to the container registry",
        optional = true,
        // read-registry-only permissions, for all gitlab private projects, expires 2023-01-01
        // revoke @ https://gitlab.com/profile/personal_access_tokens
        default = Some("Mz-TpEuBXch9n65KZKZe")
      ),
      FlagConfigEntry(
        key = "URL",
        destination = Vault,
        prompt = "URL of the container registry",
        optional = true,
        default = Some("registry.gitlab.com")
      )
    )
  )

  // Defines functions that get run for
  val flagConfigHooks: Map[String, FlagHook] =
    Map(
      "google-oauth" -> oauthConfig,
      "docker-auth" -> dockerAuthConfig,
      "okta-auth" -> oktaAuthConfig,
      "minio" -> awsAuthConfig,
      "ensure-supported" -> ensureSupported
    )

  // The list of flags that are enabled by default - "tui" only enabled by default on linux/amd64
  val flagDefaults
    : List[String] = (List("ensure-supported", "core", "dev", "docker-auth") ++ (if (ensureSupported.osname == "linux" & ensureSupported.arch == "amd64")
                                                                                   List("tui")
                                                                                 else List()))
}
