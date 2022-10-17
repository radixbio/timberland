package com.radix.timberland.flags

import java.io.File
import cats.effect.{ContextShift, IO, Resource}
import cats.implicits._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.VaultUtils
import com.radix.utils.helm
import com.radix.utils.helm.http4s.Http4sConsulClient
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult, VaultErrorResponse}
import com.radix.utils.helm.{ConsulOp, QueryResponse}
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn

/**
 * A configurable parameter for a flag
 *
 * @param key          The name to be used in consul/vault when storing the config parameter
 * @param isSensitive  If true, the flag will be stored in vault instead of consul kv
 * @param prompt       A prompt shown to the user when asking for the value of the config parameter
 * @param default      An optional value used when empty string is specified or when running non-interactively
 * @param terraformVar If specified, the value will be bound to a terraform variable with this name
 */
case class FlagConfigEntry(key: String,
                           isSensitive: Boolean,
                           prompt: String,
                           default: Option[String] = None,
                           optional: Boolean = false,
                           terraformVar: Option[String] = None)

/**
 * Response object containing data to be passed on to terraform
 *
 * @param configVars  A map of config entries directly with associated with terraform variables
 * @param definedVars A list of defined config parameters in the form flagName.configName (e.g. minio.aws_access_key_id)
 */
case class TerraformConfigVars(configVars: Map[String, String], definedVars: Iterable[String])

/**
 * A configuration object containing the flag -> configKey -> configValue maps for both consul and vault
 *
 * @param consulData A map from flag name to config key to config value, used for non-sensitive information
 * @param vaultData  A map from flag name to config key to config value, used for sensitive information
 */
case class FlagConfigs(consulData: Map[String, Map[String, Option[String]]] = Map.empty,
                       vaultData: Map[String, Map[String, Option[String]]] = Map.empty) {
  /**
   * Merges the consulData and vaultData parameters of two FlagConfigs
   *
   * @param that The right flagConfig. Values on this object take precedence over the ones on the left
   * @return A FlagConfigs object containing the keys/values in both `this` and `that`
   */
  def ++(that: FlagConfigs): FlagConfigs = {
    val mergedConsulData = mergeDoubleMap(this.consulData, that.consulData)
    val mergedVaultData = mergeDoubleMap(this.vaultData, that.vaultData)
    FlagConfigs(mergedConsulData, mergedVaultData)
  }

  // Helper method used by the above merge method
  private def mergeDoubleMap(bottom: Map[String, Map[String, Option[String]]],
                             top: Map[String, Map[String, Option[String]]]) = {
    val fullKeySet = bottom.keys.toSet ++ top.keys.toSet
    fullKeySet.map(key => key -> (bottom.getOrElse(key, Map.empty) ++ top.getOrElse(key, Map.empty))).toMap
  }
}

object flagConfig {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  /**
   * Prompts for missing flag values, writes them to vault/consul, and returns the terraform vars
   *
   * @param flagMap The current map of flags. Only config params for enabled flags will be processed
   * @return A map of terraform variables and a list of defined config parameters
   *         in the form flagName.configName (e.g. minio.aws_access_key_id)
   */
  def updateFlagConfig(flagMap: Map[String, Boolean], masterToken: Option[String])
                      (implicit serviceAddrs: ServiceAddrs, persistentDir: os.Path): IO[TerraformConfigVars] = {
    val flagList = flagMap.filter(_._2).keys.toList
    for {
      partialFlagConfig <- readFlagConfig(masterToken)
      totalFlagConfig <- addMissingParams(flagList, partialFlagConfig)
      _ <- writeFlagConfig(totalFlagConfig, masterToken)
      _ <- executeHooks(flagList, totalFlagConfig)
    } yield TerraformConfigVars(
      parseTerraformVars(totalFlagConfig.consulData) ++ parseTerraformVars(totalFlagConfig.vaultData),
      getDefinedParams(totalFlagConfig.consulData) ++ getDefinedParams(totalFlagConfig.vaultData)
    )
  }

  /**
   * Uses `terraformVar` in `config.flagConfigParams` to create a map of terraform variable name to config value
   * from either the consulData or vaultData parameters in FlagConfigs
   *
   * @param entries A map from flag name to config map to parse out terraform variable values from
   * @return A map from terraform variable name to value
   */
  private def parseTerraformVars(entries: Map[String, Map[String, Option[String]]]): Map[String, String] = {
    val terraformVarOptions = for {
      flagAndCfgEntryTuples <- entries
      flagName = flagAndCfgEntryTuples._1
      definedCfgEntry <- flagAndCfgEntryTuples._2.filter(_._2.isDefined)
      entryObj = config.flagConfigParams(flagName).find(_.key == definedCfgEntry._1).get
    } yield entryObj.terraformVar.map(_ -> definedCfgEntry._2.get)
    terraformVarOptions.flatten.toMap
  }

  /**
   * Checks for custom functions in config.flagConfigHooks and runs them with the associated config maps passed in
   *
   * @param flagList The list of flags to execute hooks for
   */
  private def executeHooks(flagList: List[String], configs: FlagConfigs)
                          (implicit serviceAddrs: ServiceAddrs): IO[Unit] =
    flagList
      .map { flagName =>
        val consulCfg = configs.consulData.getOrElse(flagName, Map.empty)
        val vaultCfg = configs.vaultData.getOrElse(flagName, Map.empty)
        if (config.flagConfigHooks contains flagName) {
          config.flagConfigHooks(flagName).map(f => f(consulCfg ++ vaultCfg, serviceAddrs)).parSequence
        } else IO.unit
      }
      .sequence.map(_ => ())

  /**
   * Creates a list used by terraform to determine which optional config variables were left blank
   *
   * @param entries A map from flag name to config map to parse out the list of defined config entries from
   * @return A list of defined config entries in the form flagName.configName (e.g. minio.aws_access_key_id)
   */
  private def getDefinedParams(entries: Map[String, Map[String, Option[String]]]): Iterable[String] =
    for {
      flagAndCfgEntryTuples <- entries
      flagName = flagAndCfgEntryTuples._1
      definedCfgEntry <- flagAndCfgEntryTuples._2.filter(_._2.isDefined)
      cfgParamName = definedCfgEntry._1
    } yield s"$flagName.$cfgParamName"

  /**
   * Reads the local (and potentially remote) flag configuration maps.
   * Local values take precedence over remote ones
   *
   * @return A flagConfig object containing the current accessible configuration
   */
  private def readFlagConfig(masterToken: Option[String])
                            (implicit serviceAddrs: ServiceAddrs, persistentDir: os.Path): IO[FlagConfigs] = {
    val topCfg = new LocalConfig().read()
    val bottomCfg = masterToken match {
      case Some(token) => flags.isConsulUp().flatMap {
        case true => new RemoteConfig(token).read()
        case false => IO.pure(FlagConfigs())
      }
      case None => IO.pure(FlagConfigs())
    }
    (bottomCfg, topCfg).parMapN(_ ++ _)
  }

  /**
   * Writes a FlagConfigs to consul and vault, or to the local flag configuration files if consul/vault are not up
   * If write is successful, clears the local config file
   *
   * @param configs The config to write
   */
  private def writeFlagConfig(configs: FlagConfigs, masterToken: Option[String])
                             (implicit serviceAddrs: ServiceAddrs, persistentDir: os.Path): IO[Unit] =
    flags.isConsulUp().flatMap {
      case true if masterToken.isDefined =>
        new RemoteConfig(masterToken.get).write(configs) *> new LocalConfig().clear()
      case _ => new LocalConfig().write(configs)
    }

  /**
   * Prompts the user for any values specified in `config.flagConfigParams` but not in the passed `partialFlagMap`
   *
   * @param flagList          A list of flags for which all configuration parameters should be defined
   * @param partialFlagConfig A potentially incomplete FlagConfigs object
   * @return A FlagConfigs object containing a full set of configuration parameters for each enabled flag
   */
  private def addMissingParams(flagList: List[String], partialFlagConfig: FlagConfigs): IO[FlagConfigs] = {
    for {
      newConsulData <- getMissingParams(flagList, sensitive = false, partialFlagConfig.consulData)
      newVaultData <- getMissingParams(flagList, sensitive = true, partialFlagConfig.vaultData)
    } yield partialFlagConfig ++ FlagConfigs(newConsulData, newVaultData)
  }

  /**
   * Helper method for `addMissingParams`
   * Prompts the user for vault or consul keys and builds a map with the responses
   *
   * @param flagList           The list of flags to ask for config values for
   * @param sensitive          If true, only vault config parameters will be prompted. If false, only consul parameters
   * @param curFlagToConfigMap The current map of flags. Anything defined here will not be prompted
   * @return A map containing only the key/values that were prompted
   */
  private def getMissingParams(flagList: List[String],
                               sensitive: Boolean,
                               curFlagToConfigMap: Map[String, Map[String, Option[String]]]
                              ): IO[Map[String, Map[String, Option[String]]]] = {
    flagList.filter(config.flagConfigParams.contains).map { flagName =>
      val totalKeysForFlag = config.flagConfigParams(flagName).filter(_.isSensitive == sensitive).map(_.key)
      val curKeysForFlag = curFlagToConfigMap.getOrElse(flagName, Map.empty).keys
      val missingKeys = totalKeysForFlag.toSet -- curKeysForFlag.toSet

      val newConfigEntriesIO = missingKeys.toList.map { missingKey =>
        val configEntry = config.flagConfigParams(flagName).find(_.key == missingKey).get
        promptUser(configEntry).map(missingKey -> _)
      }.sequence
      newConfigEntriesIO.map(newConfigEntries => flagName -> newConfigEntries.toMap)
    }.sequence.map(_.toMap)
  }

  /**
   * Prints `prompt` ("> " is automatically appended) to the command line and returns the response from stdin
   * A default value (or null if `optional`) is used when an empty string is specified or the terminal is noninteractive
   *
   * @param entry The config entry specifying the prompt string, whether the value is optional, and a default value
   * @return The response from stdin if the response is nonempty and the terminal is interactive
   */
  private def promptUser(entry: FlagConfigEntry): IO[Option[String]] = IO {
    val optionalPromptPrefix = if (entry.optional) "[Optional] " else ""
    Console.print(optionalPromptPrefix + entry.prompt + "> ")
    StdIn.readLine() match {
      case null | "" => if (entry.optional) None else Some(entry.default.getOrElse {
        Console.err.println("\nConfig option not specified with no available fallback value, quitting")
        sys.exit(1)
      })
      case response => Some(response)
    }
  }
}

class LocalConfig(implicit persistentDir: os.Path) {
  private implicit val cfgDecoder: Decoder[FlagConfigs] = deriveDecoder[FlagConfigs]
  private implicit val cfgEncoder: Encoder[FlagConfigs] = deriveEncoder[FlagConfigs]
  private val flagConfigFile = persistentDir / "config.json"

  def read(): IO[FlagConfigs] = for {
    configFileExists <- IO(os.exists(flagConfigFile))
    configFileText <- if (configFileExists) IO(os.read(flagConfigFile)) else IO.pure("")
  } yield decode(configFileText).getOrElse(FlagConfigs())

  def write(configs: FlagConfigs): IO[Unit] = {
    val cfgJsonStr = configs.asJson.toString
    val flagFile = new File(flagConfigFile.toString())
    IO {
      flagFile.setReadable(false, false)
      flagFile.setReadable(true, true)
      flagFile.setWritable(false, true)
      flagFile.setExecutable(false, true)
      os.write.over(flagConfigFile, cfgJsonStr)
    }
  }

  def clear(): IO[Unit] = IO {
    os.write.over(flagConfigFile, "")
  }
}

class RemoteConfig(masterToken: String)(implicit serviceAddrs: ServiceAddrs) {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)
  private implicit val blaze: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](global).resource

  def read(): IO[FlagConfigs] = (readFromConsul(), readFromVault()).parMapN(FlagConfigs)

  private def readFromConsul(): IO[Map[String, Map[String, Option[String]]]] = blaze.use { client =>
    val consulUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8500").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, client, Some(masterToken))
    val getCfgOp = ConsulOp.kvGetJson[Map[String, Map[String, Option[String]]]]("flag-config", None, None)
    helm.run(interpreter, getCfgOp).map {
      case Right(QueryResponse(Some(flagConfig), _, _, _)) => flagConfig
      case Right(QueryResponse(None, _, _, _)) => Map.empty
      case Left(err) =>
        Console.err.println("Error connecting to consul\n" + err)
        sys.exit(1)
    }
  }

  private def readFromVault(): IO[Map[String, Map[String, Option[String]]]] = blaze.use { client =>
    for {
      vaultToken <- IO(new VaultUtils().findVaultToken())
      vaultUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8200").toOption.get
      vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri, blazeClient = client)
      cfgJsonStr <- vault.getSecret("flag-config")
    } yield cfgJsonStr match {
      case Right(KVGetResult(_, json)) => json.as[Map[String, Map[String, Option[String]]]].getOrElse(Map.empty)
      case Left(VaultErrorResponse(Vector())) => Map.empty
      case Left(err) =>
        Console.err.println("Error connecting to vault\n" + err)
        sys.exit(1)
    }
  }

  def write(configs: FlagConfigs): IO[Unit] =
    (writeToConsul(configs), writeToVault(configs)).parMapN((_, _) => ())

  private def writeToConsul(configs: FlagConfigs): IO[Unit] = blaze.use { client =>
    val consulUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8500").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, client, Some(masterToken))
    helm.run(interpreter, ConsulOp.kvSetJson("flag-config", configs.consulData))
  }

  private def writeToVault(configs: FlagConfigs): IO[Unit] = blaze.use { client =>
    for {
      vaultToken <- IO(new VaultUtils().findVaultToken())
      // NOTE: Assumes consulAddr == vaultAddr
      vaultUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8200").toOption.get
      vault = new Vault[IO](authToken = Some(vaultToken), baseUrl = vaultUri, blazeClient = client)
      secretReq = CreateSecretRequest(data = configs.vaultData.asJson, cas = None)
      response <- if (configs.vaultData.isEmpty) IO.pure(Right()) else {
        vault.createSecret("flag-config", secretReq)
      }
    } yield response match {
      case Right(_) => ()
      case Left(err) =>
        Console.err.println("Error connecting to vault\n" + err)
        sys.exit(1)
    }
  }
}