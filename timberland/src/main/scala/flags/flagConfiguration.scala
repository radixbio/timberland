package com.radix.timberland.flags

import java.io.File
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.flags.featureFlags.{defaultFlagMap, getLocalFlags}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens

import scala.concurrent.duration._
import com.radix.timberland.util.{LogTUI, Util}
import com.radix.utils.helm
import com.radix.utils.helm.http4s.Http4sConsulClient
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault.{CreateSecretRequest, KVGetResult, VaultErrorResponse}
import com.radix.utils.helm.{ConsulOp, QueryResponse}
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import com.radix.utils.tls.ConsulVaultSSLContext._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait FlagConfigDestination
case object Vault extends FlagConfigDestination
case object Consul extends FlagConfigDestination
case object Nowhere extends FlagConfigDestination

/**
 * A configurable parameter for a flag
 *
 * @param key          The name to be used in consul/vault when storing the config parameter
 * @param destination  The location for the config parameter to be stored in. One of Consul, Vault, or Nowhere
 * @param prompt       A prompt shown to the user when asking for the value of the config parameter
 * @param default      An optional value used when empty string is specified or when running non-interactively
 * @param terraformVar If specified, the value will be bound to a terraform variable with this name
 */
case class FlagConfigEntry(
  key: String,
  destination: FlagConfigDestination,
  prompt: String,
  default: Option[String] = None,
  optional: Boolean = false,
  terraformVar: Option[String] = None
)

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
case class FlagConfigs(
  consulData: Map[String, Map[String, Option[String]]] = Map.empty,
  vaultData: Map[String, Map[String, Option[String]]] = Map.empty
) {

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
  private def mergeDoubleMap(
    bottom: Map[String, Map[String, Option[String]]],
    top: Map[String, Map[String, Option[String]]]
  ) = {
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
  def updateFlagConfig(flagMap: Map[String, Boolean])(implicit
    serviceAddrs: ServiceAddrs = ServiceAddrs(),
    persistentDir: os.Path,
    tokens: Option[AuthTokens] = None
  ): IO[TerraformConfigVars] = {
    val shouldPrompt = (defaultFlagMap ++ flagMap)("interactive")
    val flagList = flagMap.filter(_._2).keys.toList
    for {
      partialFlagConfig <- readFlagConfig
      totalFlagConfig <- addMissingParams(flagList, partialFlagConfig, shouldPrompt)
      _ <- writeFlagConfig(totalFlagConfig)
      _ <- executeHooks(flagList, totalFlagConfig)
    } yield TerraformConfigVars(
      parseTerraformVars(totalFlagConfig.consulData) ++ parseTerraformVars(totalFlagConfig.vaultData),
      getDefinedParams(totalFlagConfig.consulData) ++ getDefinedParams(totalFlagConfig.vaultData)
    )
  }

  /**
   * Prompts for default flag configuration parameters and writes them to the local config file
   */
  def promptForDefaultConfigs(implicit
    serviceAddrs: ServiceAddrs = ServiceAddrs(),
    persistentDir: os.Path
  ): IO[TerraformConfigVars] = for {
    localFlags <- getLocalFlags(persistentDir)
    configVars <- updateFlagConfig(featureFlags.defaultFlagMap ++ localFlags)
  } yield configVars

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
  private def executeHooks(flagList: List[String], configs: FlagConfigs)(implicit
    serviceAddrs: ServiceAddrs
  ): IO[Unit] =
    flagList
      .map { flagName =>
        val consulCfg = configs.consulData.getOrElse(flagName, Map.empty)
        val vaultCfg = configs.vaultData.getOrElse(flagName, Map.empty)
        if (config.flagConfigHooks contains flagName) {
          config.flagConfigHooks(flagName).run(consulCfg ++ vaultCfg, serviceAddrs)
        } else IO.unit
      }
      .sequence
      .map(_ => ())

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
   * @param authTokens Used to read data from consul and vault. If this isn't specified,
   *                   data will only be read from the local config.json file
   * @return A flagConfig object containing the current accessible configuration
   */
  def readFlagConfig(implicit
    serviceAddrs: ServiceAddrs,
    persistentDir: os.Path,
    authTokens: Option[AuthTokens]
  ): IO[FlagConfigs] = {
    val topCfg = new LocalConfig().read()
    val bottomCfg = authTokens match {
      case Some(tokens) =>
        featureFlags.isConsulUp().flatMap {
          case true  => new RemoteConfig()(serviceAddrs, tokens).read()
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
  private def writeFlagConfig(
    configs: FlagConfigs
  )(implicit serviceAddrs: ServiceAddrs, persistentDir: os.Path, tokens: Option[AuthTokens]): IO[Unit] =
    for {
      shouldWriteRemote <- if (tokens.isDefined) featureFlags.isConsulUp() else IO.pure(false)
      _ <-
        if (shouldWriteRemote) {
          new RemoteConfig()(serviceAddrs, tokens.get).write(configs) *> new LocalConfig().clear()
        } else {
          new LocalConfig().write(configs)
        }
    } yield ()

  /**
   * Prompts the user for any values specified in `config.flagConfigParams` but not in the passed `partialFlagMap`
   *
   * @param flagList          A list of flags for which all configuration parameters should be defined
   * @param partialFlagConfig A potentially incomplete FlagConfigs object
   * @return A FlagConfigs object containing a full set of configuration parameters for each enabled flag
   */
  def addMissingParams(flagList: List[String], partialFlagConfig: FlagConfigs, prompt: Boolean): IO[FlagConfigs] = {
    for {
      newConsulData <- getMissingParams(flagList, destination = Consul, partialFlagConfig.consulData, prompt)
      newVaultData <- getMissingParams(flagList, destination = Vault, partialFlagConfig.vaultData, prompt)
    } yield partialFlagConfig ++ FlagConfigs(newConsulData, newVaultData)
  }

  /**
   * Helper method for `addMissingParams`
   * Prompts the user for vault or consul keys and builds a map with the responses
   *
   * @param flagList           The list of flags to ask for config values for
   * @param destination        Whether to write flags to consul, vault, or neither
   * @param curFlagToConfigMap The current map of flags. Anything defined here will not be prompted
   * @param shouldConfirm      Whether to prompt the user to confirm pushing changes to consul
   * @return A map containing only the key/values that were prompted
   */
  def getMissingParams(
    flagList: List[String],
    destination: FlagConfigDestination,
    curFlagToConfigMap: Map[String, Map[String, Option[String]]],
    shouldPrompt: Boolean
  ): IO[Map[String, Map[String, Option[String]]]] = {
    flagList
      .filter(config.flagConfigParams.contains)
      .map { flagName =>
        val totalKeysForFlag = config.flagConfigParams(flagName).filter(_.destination == destination).map(_.key)
        val curKeysForFlag = curFlagToConfigMap.getOrElse(flagName, Map.empty).keys
        val missingKeys = totalKeysForFlag.toSet -- curKeysForFlag.toSet

        val newConfigEntriesIO = missingKeys.toList.map { missingKey =>
          val configEntry = config.flagConfigParams(flagName).find(_.key == missingKey).get
          promptUser(configEntry, shouldPrompt).map(missingKey -> _)
        }.sequence
        newConfigEntriesIO.map(newConfigEntries => flagName -> newConfigEntries.toMap)
      }
      .sequence
      .map(_.toMap)
  }

  /**
   * Prints `prompt` ("> " is automatically appended) to the command line and returns the response from stdin
   * A default value (or null if `optional`) is used when an empty string is specified or the terminal is noninteractive
   *
   * @param entry The config entry specifying the prompt string, whether the value is optional, and a default value
   * @return The response from stdin if the response is nonempty and the terminal is interactive
   */
  private def promptUser(entry: FlagConfigEntry, shouldPrompt: Boolean): IO[Option[String]] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
    for {
      _ <- LogTUI.acquireScreen()
      userInput <- if (shouldPrompt) {
        Util.promptForString((if (entry.optional) "[Optional] " else "") + entry.prompt)
      } else IO.pure(entry.default)
      _ <- LogTUI.releaseScreen()
      maybeUserInput <- IO {
        userInput.orElse(entry.default).orElse {
          if (entry.optional) {
            None
          } else {
            Console.err.println("\nConfig option not specified with no available fallback value, quitting")
            sys.exit(1)
          }
        }
      }
    } yield maybeUserInput
  }
}

class LocalConfig(implicit persistentDir: os.Path) {
  private implicit val cfgDecoder: Decoder[FlagConfigs] = deriveDecoder[FlagConfigs]
  private implicit val cfgEncoder: Encoder[FlagConfigs] = deriveEncoder[FlagConfigs]
  private val flagConfigFile = persistentDir / "config.json"

  def read(): IO[FlagConfigs] =
    for {
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

class RemoteConfig(implicit serviceAddrs: ServiceAddrs, tokens: AuthTokens) {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def read(): IO[FlagConfigs] = (readFromConsul(), readFromVault()).parMapN(FlagConfigs)
  private def readFromConsul(): IO[Map[String, Map[String, Option[String]]]] = {
    val consulUri = Uri.fromString(s"https://${serviceAddrs.consulAddr}:8501").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, Some(tokens.consulNomadToken))
    val getCfgOp = ConsulOp.kvGetJson[Map[String, Map[String, Option[String]]]]("flag-config", None, None)
    helm.run(interpreter, getCfgOp).map {
      case Right(QueryResponse(Some(flagConfig), _, _, _)) => flagConfig
      case Right(QueryResponse(None, _, _, _))             => Map.empty
      case Left(err) =>
        Console.err.println("Error connecting to consul\n" + err)
        sys.exit(1)
    }
  }

  private def readFromVault(): IO[Map[String, Map[String, Option[String]]]] = {
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    val vault = new VaultSession[IO](authToken = Some(tokens.vaultToken), baseUrl = vaultUri)
    vault.getSecret("flag-config").map {
      case Right(KVGetResult(_, json))        => json.as[Map[String, Map[String, Option[String]]]].getOrElse(Map.empty)
      case Left(VaultErrorResponse(Vector())) => Map.empty
      case Left(err) =>
        Console.err.println("Error connecting to vault\n" + err)
        sys.exit(1)
    }
  }

  def write(configs: FlagConfigs): IO[Unit] =
    (writeToConsul(configs), writeToVault(configs)).parMapN((_, _) => ())

  private def writeToConsul(configs: FlagConfigs): IO[Unit] = {
    val consulUri = Uri.fromString(s"https://${serviceAddrs.consulAddr}:8501").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, Some(tokens.consulNomadToken))
    helm.run(interpreter, ConsulOp.kvSetJson("flag-config", configs.consulData))
  }

  private def writeToVault(configs: FlagConfigs): IO[Unit] = {
    val vaultUri = Uri.fromString(s"https://${serviceAddrs.vaultAddr}:8200").toOption.get
    val vault = new VaultSession[IO](authToken = Some(tokens.vaultToken), baseUrl = vaultUri)
    val secretReq = CreateSecretRequest(data = configs.vaultData.asJson, cas = None)
    if (configs.vaultData.nonEmpty) {
      vault.createSecret("flag-config", secretReq).map {
        case Right(_) => ()
        case Left(err) =>
          Console.err.println("Error connecting to vault\n" + err)
          sys.exit(1)
      }
    } else IO.unit
  }
}
