package com.radix.timberland.flags

import java.net.InetAddress

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{LogTUI, Util}
import com.radix.utils.helm
import com.radix.utils.helm.http4s.Http4sConsulClient
import com.radix.utils.helm.{ConsulOp, QueryResponse}
import com.radix.utils.tls.ConsulVaultSSLContext._
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import io.circe.{Decoder, Json}
import org.http4s.Uri

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.AnsiColor

sealed trait FlagUpdateResponse

case class ConsulFlagsUpdated(flags: Map[String, Boolean]) extends FlagUpdateResponse

case class FlagsStoredLocally() extends FlagUpdateResponse

case class ModuleDefinition(key: String, source: String, dir: String)

object featureFlags {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)
  private implicit val moduleDecoder: Decoder[ModuleDefinition] =
    Decoder.forProduct3("Key", "Source", "Dir")(ModuleDefinition.apply)

  private val flagFile = os.rel / "terraform" / "flags.json"

  // A list of flags which don't have any relation to modules
  private val specialFlags =
    Set("dev", "google-oauth", "docker-auth", "okta-auth", "tui", "remote_images", "interactive")

  // A map from flag name to a list of module names // may no longer necessary?
  private val flagSupersets = Map(
    "core" -> Set(
      "kafka",
      "kafka_companions",
      "minio",
      "nginx",
      "retool",
      "retool_pg_kafka_connector",
      "zookeeper"
    ),
    "device_drivers" -> Set(
      "apprise",
      "ln2",
      "quantstudio",
      "opentrons",
      "multitrons",
      "hw_discovery",
      "tf_exactive",
      "osipi_connector",
      "elemental_bridge",
      "eve",
      "minifors2",
      "mock_bioreactor",
      "omnidriver",
      "ht91100",
      "eth_multitrons",
      "watlow96_thermal",
      "octet",
      "tecan_driver"
    ),
    "algs" -> Set(
      "algs_mega_uservice"
    ),
    "utils" -> Set(
      "s3lts",
      "bunny_uservice"
    ),
    "kafka" -> Set(
      "kafka",
      "kafka_companions"
    ),
    "retool" -> Set(
      "retool"
    ),
    "server" -> Set(
      "web_interface"
    )
  )
  // All flags that aren't tied to a specific module
  private val nonModuleFlags = specialFlags ++ flagSupersets.keySet + "all"

  def defaultFlagMap = resolveSupersetFlags(config.flagDefaults.map(_ -> true).toMap)

  /**
   * Sets a feature flag either in Consul or in the local flag file (if Consul is not running)
   * If Consul is up, this also pushes pending changes in the local flag file. If flags is empty,
   * this function only pushes the pending changes.
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland/terraform
   * @param tokens        Contains the consul token used to update flags. If this isn't set, the function
   *                      will always write to the local flag file
   * @param flagsToSet    A map of new flags to push
   * @param confirm       Whether to prompt the user before submitting flags to consul
   * @return The current state of all known feature flags
   */
  def updateFlags(
    persistentDir: os.Path,
    tokens: Option[AuthTokens],
    flagsToSet: Map[String, Boolean] = Map.empty,
    confirm: Boolean = false
  )(implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Map[String, Boolean]] = {
    for {
      validFlags <- validateFlags(persistentDir, flagsToSet, tokens)(serviceAddrs)
      actualFlags = resolveSupersetFlags(flagsToSet, validFlags)
      localFlags <- getLocalFlags(persistentDir)
      totalFlags = localFlags ++ actualFlags
      shouldPrompt = (defaultFlagMap ++ totalFlags)("interactive")
      shouldUpdateConsul <- tokens match {
        case Some(_) => isConsulUp()
        case None    => IO.pure(false)
      }
      _ <- callNonpersistentFlagHooks(flagsToSet, shouldPrompt)
      newFlags <-
        if (shouldUpdateConsul) {
          for {
            _ <-
              if (shouldPrompt && confirm) {
                confirmFlags(persistentDir, shouldUpdateConsul, serviceAddrs, tokens, totalFlags)
              } else IO.unit
            _ <- clearLocalFlagFile(persistentDir, totalFlags)
            newFlags <- setConsulFlags(totalFlags)(serviceAddrs, tokens.get)
          } yield newFlags
        } else {
          setLocalFlags(persistentDir, actualFlags)
        }
    } yield newFlags
  }

  def getConsulFlags(implicit serviceAddrs: ServiceAddrs, tokens: AuthTokens): IO[Map[String, Boolean]] = {
    val consulUri = Uri.fromString(s"https://${serviceAddrs.consulAddr}:8501").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, Some(tokens.consulNomadToken))
    val getFeaturesOp = ConsulOp.kvGetJson[Map[String, Boolean]]("features", None, None)
    for {
      features <- helm.run(interpreter, getFeaturesOp)
    } yield features match {
      case Right(QueryResponse(Some(consulFlagMap), _, _, _)) =>
        defaultFlagMap ++ consulFlagMap
      case _ =>
        defaultFlagMap
    }
  }

  /**
   * Pushes a map of flags to Consul
   *
   * @param flags The flags to push
   * @return The total set of all flags on Consul after pushing
   */
  private def setConsulFlags(
    flags: Map[String, Boolean]
  )(implicit serviceAddrs: ServiceAddrs, tokens: AuthTokens): IO[Map[String, Boolean]] = {
    val consulUri = Uri.fromString(s"https://${serviceAddrs.consulAddr}:8501").toOption.get
    val interpreter = new Http4sConsulClient[IO](consulUri, Some(tokens.consulNomadToken))
    val setFeaturesOp = ConsulOp.kvSetJson("features", _: Map[String, Boolean])
    for {
      curFlags <- getConsulFlags
      _ <- helm.run(interpreter, setFeaturesOp(curFlags ++ flags))
    } yield curFlags ++ flags
  }

  /**
   * Reads the local flag file to get the "dev" setting along with any pending feature flag changes
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return A map of flags present in the local flag file
   */
  def getLocalFlags(persistentDir: os.Path): IO[Map[String, Boolean]] = {
    val flagFileLocation = flagFile resolveFrom persistentDir
    for {
      flagFileExists <- IO(os.exists(flagFileLocation))
      flagFileText <- if (flagFileExists) IO(os.read(flagFileLocation)) else IO.pure("")
    } yield {
      decode[Map[String, Boolean]](flagFileText).getOrElse(Map.empty)
    }
  }

  def isConsulUp()(implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Boolean] = {
    IO(InetAddress.getAllByName(serviceAddrs.consulAddr)).attempt.map {
      case Left(_) | Right(Array()) => false
      case _                        => true
    }
  }

  /**
   * Store a set of feature flags locally on the disk. These will be pushed to Consul next time
   * timberland connects to Consul
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @param flags         The flags to set in the local flag file
   * @return The updated contents of the flag file
   */
  private def setLocalFlags(persistentDir: os.Path, flags: Map[String, Boolean]): IO[Map[String, Boolean]] = {
    for {
      oldFlags <- getLocalFlags(persistentDir)
      newFlags = oldFlags ++ flags
      newFlagsJson = newFlags.asJson.toString()
      _ <- IO(os.write.over(flagFile resolveFrom persistentDir, newFlagsJson))
    } yield newFlags
  }

  /**
   * Removes all flags except special flags from the local file. This is so that if another computer
   * changes the feature flags on Consul, starting timberland on the original computer won't overwrite them
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @param contents      List of all flags (used to persist special flags)
   * @return Nothing
   */
  private def clearLocalFlagFile(persistentDir: os.Path, contents: Map[String, Boolean]): IO[Unit] = {
    if (contents.nonEmpty) {
      // Persist any special flags so they can be retrieved before consul is started
      val newJson = specialFlags
        .map(flag =>
          flag -> {
            val defaultSpecialFlagVal = defaultFlagMap.getOrElse(flag, false)
            contents.getOrElse(flag, defaultSpecialFlagVal)
          }
        )
        .toMap
        .asJson
        .toString()

      IO(os.write.over(flagFile resolveFrom persistentDir, newJson))
    } else IO.unit
  }

  /**
   * Throws an error if any of the flags specified in `flags` is invalid
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return The total list of valid flags that was validated against
   */
  private def validateFlags(persistentDir: os.Path, flags: Map[String, Boolean], authTokens: Option[AuthTokens])(
    implicit serviceAddrs: ServiceAddrs
  ): IO[Set[String]] = {
    for {
      validFlags <- getValidFlags(persistentDir, authTokens)
    } yield {
      val invalidFlags = flags.keySet -- validFlags -- nonModuleFlags
      if (invalidFlags.isEmpty) validFlags
      else {
        scribe.error("Invalid flags specified: " + invalidFlags.mkString(", "))
        sys.exit(1)
      }
    }
  }

  /**
   * Auto-detects a list of valid flags, each corresponding with a terraform module or a member of nonModuleFlags
   *
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return A list of valid flags
   */
  private def getValidFlags(persistentDir: os.Path, tokens: Option[AuthTokens])(implicit
    serviceAddrs: ServiceAddrs = ServiceAddrs()
  ): IO[Set[String]] = {
    val moduleFile = persistentDir / os.up / "terraform" / ".terraform" / "modules" / "modules.json"
    for {
      _ <- daemonutil.initTerraform(false, tokens.map(_.consulNomadToken))
      fileExists <- IO(os.exists(moduleFile))
      maybeModulesText <- if (fileExists) IO(Some(os.read(moduleFile))) else IO.pure(None)
    } yield maybeModulesText match {
      case None => Set[String]() // bail iff tform init failed
      case Some(modulesText) =>
        val modulesJson = parse(modulesText).getOrElse(Json.Null)
        modulesJson.hcursor
          .get[List[ModuleDefinition]]("Modules")
          .toOption
          .get
          .map { case ModuleDefinition(key, _, _) => key }
          .filter(name => name.nonEmpty)
          .toSet
    }
  }

  /**
   * If you call `timberland enable <flag>` and <flag> is has config options where destination = Nowhere,
   * then this function will prompt you for those config options and call the associated hooks for <flag>
   *
   * @param flagsToSet A list of flags that were enabled or disabled
   * @param addrs      Service addresses
   * @return Nothing
   */
  def callNonpersistentFlagHooks(flagsToSet: Map[String, Boolean], shouldPrompt: Boolean)
                                (implicit addrs: ServiceAddrs): IO[Unit] = {
    val flagList = flagsToSet.filter(_._2).keys.toList
    for {
      configResponses <- flagConfig.getMissingParams(
        flagList,
        destination = Nowhere,
        curFlagToConfigMap = Map.empty,
        shouldPrompt
      )
      _ <- flagList
        .filter(config.flagConfigHooks.contains)
        .map { flagName =>
          config.flagConfigHooks(flagName).run(configResponses.getOrElse(flagName, Map.empty), addrs)
        }
        .parSequence
    } yield ()
  }

  /**
   * @param flagsToSet A list of changes to apply (e.g. all -> true)
   * @param validFlags A set of valid flags. Used when "all"
   * @return The resolved list of variables to change (e.g. kafka -> true, etc)
   */
  def resolveSupersetFlags(
    flagsToSet: Map[String, Boolean],
    validFlags: Set[String] = Set.empty
  ): Map[String, Boolean] = {
    val supersetsWithAll = flagSupersets + ("all" -> validFlags)
    flagsToSet -- supersetsWithAll.keys ++ supersetsWithAll.toList.flatMap {
      case (supersetFlagName, flagSet) if flagsToSet contains supersetFlagName =>
        flagSet.map(flag => (flag, flagsToSet(supersetFlagName) && flagsToSet.getOrElse(flag, true)))
      case _ => Map.empty
    }.toMap
  }

  def confirmFlags(
    persistentDir: os.Path,
    consulIsUp: Boolean,
    serviceAddrs: ServiceAddrs,
    authTokens: Option[AuthTokens],
    extraFlags: Map[String, Boolean] = Map.empty
  ): IO[Unit] = {
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
    for {
      pendingChangesExist <- printFlagInfo(persistentDir, consulIsUp, serviceAddrs, authTokens, extraFlags)
      _ <- LogTUI.acquireScreen()
      shouldContinue <- if (pendingChangesExist) {
        Util.promptForBool("The above changes will be written to consul/vault. Continue?")
      } else IO.pure(true)
      _ <- LogTUI.releaseScreen()
    } yield {
      if (shouldContinue) () else sys.exit(0)
    }
  }

  /**
   * Prints the current state of flags along with any pending changes
   *
   * @return True if there are any pending changes
   */
  def printFlagInfo(
    persistentDir: os.Path,
    consulIsUp: Boolean,
    serviceAddrs: ServiceAddrs,
    authTokens: Option[AuthTokens],
    extraFlags: Map[String, Boolean] = Map.empty
  ): IO[Boolean] =
    for {
      localFlagFileContents <- featureFlags.getLocalFlags(persistentDir)
      localFlags = localFlagFileContents ++ extraFlags
      remoteFlags <-
        if (consulIsUp) {
          featureFlags.getConsulFlags(serviceAddrs, authTokens.get)
        } else IO.pure(Map[String, Boolean]())

      localFlagConfig <- new LocalConfig()(persistentDir).read()
      remoteFlagConfig <-
        if (consulIsUp) {
          new RemoteConfig()(serviceAddrs, authTokens.get).read()
        } else IO.pure(FlagConfigs())

      _ <- if (consulIsUp) printCurrentFlags(remoteFlags, remoteFlagConfig, persistentDir) else IO.unit
    } yield printPendingFlagChanges(remoteFlags, remoteFlagConfig, localFlags, localFlagConfig)

  private def printCurrentFlags(
    remoteFlags: Map[String, Boolean],
    remoteFlagConfig: FlagConfigs,
    persistentDir: os.Path
  ): IO[Unit] = getValidFlags(persistentDir, None).map { validFlags =>
    LogTUI.printAfter("\nCurrent Flags:")
    for (flagName <- validFlags) {
      val isFlagEnabledOnRemote = remoteFlags.getOrElse(flagName, defaultFlagMap.getOrElse(flagName, false))
      val status = if (isFlagEnabledOnRemote) AnsiColor.GREEN + "ENABLED" else AnsiColor.RED + "DISABLED"
      LogTUI.printAfter(s"  $flagName: $status${AnsiColor.RESET}")
      val remoteConfigMap = remoteFlagConfig.vaultData.getOrElse(flagName, Map.empty) ++
        remoteFlagConfig.consulData.getOrElse(flagName, Map.empty)
      for (remoteConfigEntry <- remoteConfigMap) if (remoteConfigEntry._2.isDefined) {
        LogTUI.printAfter(s"    ${remoteConfigEntry._1} = ${remoteConfigEntry._2.get}")
      }
    }
  }

  private def printPendingFlagChanges(
    remoteFlags: Map[String, Boolean],
    remoteFlagConfig: FlagConfigs,
    localFlags: Map[String, Boolean],
    localFlagConfig: FlagConfigs
  ): Boolean = {
    val pendingChangeLines = localFlags.flatMap { localFlag =>
      val flagName = localFlag._1
      val isEnabled = localFlag._2
      val resetStr = AnsiColor.RESET + AnsiColor.BOLD // should use jansi
      val remoteCfgMap = remoteFlagConfig.vaultData.getOrElse(flagName, Map.empty) ++
        remoteFlagConfig.consulData.getOrElse(flagName, Map.empty)
      val localCfgMap = localFlagConfig.vaultData.getOrElse(flagName, Map.empty) ++
        localFlagConfig.vaultData.getOrElse(flagName, Map.empty)
      val configDelta = localCfgMap.filter { tuple =>
        tuple._2.isDefined && (!(remoteCfgMap contains tuple._1) || remoteCfgMap(tuple._1) != tuple._2)
      }
      val wasEnabled = remoteFlags.getOrElse(flagName, defaultFlagMap.getOrElse(flagName, false))
      val hasChanges = wasEnabled != isEnabled
      if (hasChanges) {
        val fromStatus = if (wasEnabled) AnsiColor.GREEN + "ENABLED" else AnsiColor.RED + "DISABLED"
        val toStatus = if (isEnabled) AnsiColor.GREEN_B + "ENABLED" else AnsiColor.RED_B + "DISABLED"
        val statusLine = s"  $flagName: $fromStatus$resetStr -> $toStatus$resetStr"
        val cfgLines = configDelta.map(changedCfgEntry => s"    ${changedCfgEntry._1} = ${changedCfgEntry._2.get}")
        statusLine +: cfgLines.toList
      } else Iterable.empty
    }
    if (pendingChangeLines.nonEmpty) {
      LogTUI.printAfter(AnsiColor.BOLD)
      LogTUI.printAfter(s"Pending Local Flag Changes:")
      pendingChangeLines.foreach(LogTUI.printAfter)
      LogTUI.printAfter(AnsiColor.RESET)
    }
    remoteFlags.nonEmpty && pendingChangeLines.nonEmpty
  }
}
