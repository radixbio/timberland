package com.radix.timberland.flags

import cats.effect.IO
import com.bertramlabs.plugins.hcl4j.HCLParser
import com.bertramlabs.plugins.hcl4j.RuntimeSymbols._
import com.radix.timberland.util.RadPath
import io.circe.Json
import io.circe.syntax._
import java.util.{Map => JavaMap, List => JavaList}
import scala.jdk.CollectionConverters._
import com.radix.timberland.ConstPaths.{TF_CONFIG_DIR, TF_MODULES_DIR}

object tfParser {

  // Returns a list of terraform modules present in the module folder
  def getModuleList: IO[List[String]] = IO {
    os.list(TF_MODULES_DIR).filter(os.isDir).map(_.last).toList
  }

  // Checks the config file associated with $moduleName and returns a list of variables in it that need to be set
  def getMissingCfgVars(moduleName: String): IO[Set[String]] = {
    for {
      varMap <- parseVars(moduleName)
      userConfig <- configGen.getConfig(moduleName)
      cfgVars =
        if (featureFlags.HOOKS.contains(moduleName)) {
          featureFlags.HOOKS(moduleName).possibleOptions
        } else varMap.keySet
      definedCfgVars = userConfig.filter(!_._2.isNull).keySet
      missingCfgVars = cfgVars -- definedCfgVars
    } yield missingCfgVars
  }

  // Returns a map containing the default value of each property defined in the "config" input variable of a tf module
  // Uses null for properties that don't have defaults
  def parseVars(moduleName: String): IO[Map[String, Json]] = getHclCfg(moduleName).map {
    case Some(hclCfg) =>
      val types = hclCfg.get("type").asInstanceOf[JavaMap[String, PrimitiveType]]
      val variables = types.keySet().toArray.map(_.toString).toList
      if (hclCfg.containsKey("default")) {
        val defaults = hclCfg.get("default").asInstanceOf[JavaMap[String, Object]]
        val values = variables.map { varName =>
          if (defaults.containsKey(varName)) {
            primitiveToJson(defaults.get(varName), types.get(varName))
          } else Json.Null
        }
        variables.zip(values).toMap
      } else {
        variables.map(_ -> Json.Null).toMap
      }
    case None =>
      Map.empty[String, Json]
  }

  // Parses the variables.tf file of a module and returns a set listing each input variable defined in the module
  def getHclVarList(moduleName: String): IO[Set[String]] = {
    parseHcl(moduleName).map(_.keySet().asScala.toSet)
  }

  def getHclDeps(moduleName: String): IO[List[String]] = {
    parseHcl(moduleName).map { hclVars =>
      if (hclVars.containsKey("dependencies")) {
        val depStanza = hclVars.get("dependencies").asInstanceOf[JavaMap[String, Object]]
        if (depStanza.containsKey("default")) {
          depStanza.get("default").asInstanceOf[JavaList[String]].asScala.toList
        } else List.empty
      } else List.empty
    }
  }

  // Parses the variables.tf file of a module and returns a map containing the type and default values of its config var
  def getHclCfg(moduleName: String): IO[Option[JavaMap[String, AnyRef]]] = {
    parseHcl(moduleName).map { hclVars =>
      if (hclVars.containsKey("config")) {
        Some(hclVars.get("config").asInstanceOf[JavaMap[String, AnyRef]])
      } else None
    }
  }

  def parseHcl(moduleName: String): IO[JavaMap[String, AnyRef]] = {
    val varFile = TF_MODULES_DIR / moduleName / "variables.tf"
    IO(os.read(varFile)).map { hclStr =>
      val objectRegex = raw"(?s)object\((\{.+})\)".r
      val hclStrSanitized = objectRegex.replaceAllIn(hclStr, "$1")
      val rootHcl = new HCLParser().parse(hclStrSanitized)
      rootHcl.get("variable").asInstanceOf[JavaMap[String, AnyRef]]
    }
  }

  // Given a string and a HCL4J type, returns a json representation of that string
  def stringToJson(value: String, valueType: PrimitiveType): Json = valueType match {
    case _: NumberPrimitiveType  => value.toDouble.asJson
    case _: BooleanPrimitiveType => value.toBoolean.asJson
    case _: StringPrimitiveType  => value.asJson
    case _                       => throw new Exception(s"Invalid config subtype found in variables.tf when parsing $value: $valueType")
  }

  // Given a Java object of unknown type and a HCL4J type, returns a json representation of that object
  private def primitiveToJson(value: Object, valueType: PrimitiveType) = valueType match {
    case _: NumberPrimitiveType  => value.asInstanceOf[Double].asJson
    case _: BooleanPrimitiveType => value.asInstanceOf[Boolean].asJson
    case _: StringPrimitiveType  => value.asInstanceOf[String].asJson
    case _                       => throw new Exception(s"Invalid config subtype found in variables.tf when parsing $value: $valueType")
  }
}
