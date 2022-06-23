package com.radix.timberland.flags

import cats.effect.IO
import cats.implicits._
import com.radix.timberland.flags.hooks._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.RadPath
import io.circe.parser._
import io.circe.syntax._

import scala.io.AnsiColor

case object featureFlags {

  // A list of flags that will be passed as terraform input vars to each module
  val SHARED_FLAGS = Set("dev", "remote_img")

  // A map from flag name to a FlagHook object who's run function will run when that flag is enabled
  val HOOKS = Map(
    "google-oauth" -> oauthConfig,
    "okta-auth" -> oktaAuthConfig,
    "messaging" -> messagingConfig,
    "ensure-supported" -> ensureSupported
  )

  // A list of flags that should be enabled by default
  val DEFAULT_FLAG_NAMES = List(
    "ensure-supported",
    "dev",
    "ipfs",
    "runtime"
  )

  val FLAGS_JSON = RadPath.runtime / "config" / "flags.json"

  // Generates all the files which dynamically depend on the modules present in timberland/terraform
  def generateAllTfAndConfigFiles: IO[Unit] = writeFlagsJson *>
    configGen.writeConfigFiles *>
    tfGen.writeMainTf *>
    tfGen.writeConfigVarsTf

  // Returns the contents of flags.json as a Map
  def flags: IO[Map[String, Boolean]] = for {
    flagStr <- IO(os.read(FLAGS_JSON))
    flagMap = parse(flagStr)
      .flatMap { json =>
        json.hcursor.downField("feature_flags").as[Map[String, Boolean]]
      }
      .left
      .map { err =>
        scribe.error(err.toString)
        sys.exit(1)
      }
      .merge
  } yield flagMap

  def query: IO[Unit] = flags.map { flagMap =>
    flagMap.toList
      .foreach { case (flag, enabled) =>
        val enableStr = if (enabled) AnsiColor.GREEN_B + "ENABLED" else AnsiColor.RED_B + "DISABLED"
        println(s"$flag: $enableStr${AnsiColor.RESET}")
      }
  }

  // Sets the passed flags to either true or false depending on the $enable variable and writes the new file to disk
  def setFlags(flagNames: List[String], enable: Boolean): IO[Unit] = for {
    flagMap <- flags
    flagsToSet <- if (flagNames.contains("all")) flags.map(_.keys.filter(_ != "dev")) else IO.pure(flagNames)
    flagDeps <- if (enable) depGraph.getTransitiveDeps(flagsToSet.toSet) else IO.pure(Set.empty[String])
    allFlagsToSet = flagsToSet.toSet ++ flagDeps
    newFlagMap = flagMap ++ allFlagsToSet.map(_ -> enable).toMap
    newJson = Map("feature_flags" -> newFlagMap)
    _ <- IO(os.write.over(FLAGS_JSON, newJson.asJson.toString()))
  } yield ()

  // Creates (or updates) a flags.json file containing a boolean property for each valid flag
  def writeFlagsJson: IO[Unit] = for {
    tfModuleNames <- tfParser.getModuleList
    flagList = tfModuleNames.toSet ++ HOOKS.keySet ++ SHARED_FLAGS
    oldFlagMap <- IO(os.exists(FLAGS_JSON)).flatMap {
      case true  => featureFlags.flags
      case false => IO.pure(Map.empty[String, Boolean])
    }
    flagMap = flagList.map(flag => flag -> DEFAULT_FLAG_NAMES.contains(flag)).toMap ++ oldFlagMap
    flagJson = Map("feature_flags" -> flagMap).asJson
    _ <- IO(os.write.over(RadPath.runtime / "config" / "flags.json", flagJson.toString()))
  } yield ()

  // Runs the "run" function on each enabled hook with the config vars for that hook
  def runHooks(implicit tokens: AuthTokens, serviceAddrs: ServiceAddrs): IO[Unit] = for {
    flags <- featureFlags.flags
    enabledHookNames = flags.filter(_._2).keys.filter(HOOKS.contains)
    _ <- enabledHookNames.toList.map { hookName =>
      for {
        rawCfg <- configGen.getConfig(hookName)
        cfg = rawCfg.view.mapValues(_.as[String].getOrElse("")).toMap
        _ <- HOOKS(hookName).run(cfg, serviceAddrs, tokens)
      } yield ()
    }.sequence
  } yield ()
}
