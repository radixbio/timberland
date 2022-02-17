package com.radix.timberland.flags

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.bertramlabs.plugins.hcl4j.RuntimeSymbols._
import io.circe.Json
import io.circe.syntax._
import io.circe.parser._

import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.io.StdIn
import com.radix.timberland.ConstPaths

object configGen {

  private val ec = ExecutionContext.global
  implicit private val cs: ContextShift[IO] = IO.contextShift(ec)

  // Create a config file in config/modules for each terraform module
  def writeConfigFiles: IO[Unit] = tfParser.getModuleList.flatMap {
    _.map(writeConfigFile).parSequence.map(_ => ())
  }

  // Create a config file in config/modules for a specific terraform module
  private def writeConfigFile(moduleName: String): IO[Unit] = for {
    defaults <-
      if (featureFlags.HOOKS.contains(moduleName)) {
        IO.pure(featureFlags.HOOKS(moduleName).possibleOptions.map(_ -> Json.Null).toMap)
      } else {
        tfParser.parseVars(moduleName)
      }
    curConfig <- getConfig(moduleName)
    newConfig = defaults ++ curConfig
    configFile = ConstPaths.TF_CONFIG_DIR / s"$moduleName.json"
    json = Map(s"config_$moduleName" -> newConfig).asJson.toString()
    _ <- if (newConfig.nonEmpty) IO(os.write.over(configFile, json)) else IO.unit
  } yield ()

  /**
   * Prompt the user for every variable of every flag
   * @param onlyMissing If true, defined variables will be skipped
   */
  def setAllConfigValues(onlyMissing: Boolean): IO[Unit] = featureFlags.flags.flatMap {
    _.filter(_._2).keys.toList.map(setConfigValues(_, onlyMissing)).sequence.map(_ => ())
  }

  /**
   * Prompt the user for every variable of a specified flag
   * @param moduleName The flag to prompt for
   * @param onlyMissing If true, defined variables will be skipped
   */
  def setConfigValues(moduleName: String, onlyMissing: Boolean): IO[Unit] = for {
    curConfigMap <- getConfig(moduleName)
    hclCfg <- tfParser.getHclCfg(moduleName)
    types =
      if (featureFlags.HOOKS.contains(moduleName)) {
        featureFlags.HOOKS(moduleName).possibleOptions.map(_ -> new StringPrimitiveType(0, 0, 0)).toMap
      } else {
        hclCfg.map(_.get("type").asInstanceOf[JavaMap[String, PrimitiveType]].asScala.toMap).getOrElse(Map.empty)
      }
    cfgValuesToSet <- if (onlyMissing) tfParser.getMissingCfgVars(moduleName) else IO.pure(types.keySet)
    _ <- cfgValuesToSet
      .map { missingKey =>
        val fallback = curConfigMap.get(missingKey).filterNot(_.isNull).map(_.toString())
        stdinPrompt(missingKey, fallback).flatMap(setConfigValue(moduleName, types, missingKey, _))
      }
      .toSeq
      .sequence
  } yield ()

  /**
   * Set a config parameter in a specific module configuration file
   * @param moduleName The module to set the config parameter in
   * @param types A map specifying which type each config parameter in the module is
   * @param key The key to set
   * @param value The value to set the key to
   */
  private def setConfigValue(moduleName: String, types: Map[String, PrimitiveType], key: String, value: String) = for {
    curCfgMap <- getConfig(moduleName)
    valueJson = tfParser.stringToJson(value, types(key))
    newCfgMap = curCfgMap + (key -> valueJson)
    newCfg = Map(s"config_$moduleName" -> newCfgMap)
    _ <- IO(os.write.over(ConstPaths.TF_CONFIG_DIR / s"$moduleName.json", newCfg.asJson.toString()))
  } yield ()

  // Returns a map specifying the current config for a module
  def getConfig(moduleName: String): IO[Map[String, Json]] = {
    val configFile = ConstPaths.TF_CONFIG_DIR / s"$moduleName.json"
    for {
      configExists <- IO(os.exists(configFile))
      config <-
        if (configExists) {
          IO(os.read(configFile)).map {
            parse(_)
              .flatMap(_.hcursor.downField(s"config_$moduleName").as[Map[String, Json]])
              .left
              .map { err =>
                scribe.error(s"Error parsing $moduleName.json: $err")
                sys.exit(1)
              }
              .merge
          }
        } else IO.pure(Map.empty[String, Json])
    } yield config
  }

  private def stdinPrompt(varName: String, fallback: Option[String] = None): IO[String] = IO {
    Console.print(s"$varName${fallback.map(x => s" [$x]").getOrElse("")}> ")
    val response = StdIn.readLine().stripLineEnd
    if (response.isEmpty) fallback.getOrElse("") else response
  }
}
