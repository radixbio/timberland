package com.radix.timberland.flags

import cats.effect.{ContextShift, IO}
import cats.implicits._

import scala.concurrent.ExecutionContext
import com.radix.timberland.ConstPaths

case object tfGen {

  // A list of root-level terraform variables that should be passed to each module
  val SHARED_VARS = List("namespace")

  private val ec = ExecutionContext.global
  implicit private val cs: ContextShift[IO] = IO.contextShift(ec)

  // Makes a main.tf file that automatically loads all the modules in timberland/terraform/modules
  def writeMainTf: IO[Unit] = for {
    modules <- tfParser.getModuleList
    mainTfContents <- mkMainTf(modules)
    mainTfLocation = ConstPaths.TF_MODULES_DIR / "main.tf"
    _ <- IO(os.write.over(mainTfLocation, mainTfContents))
  } yield ()

  // Creates a file in the terraform module directory defining each a map variable called config_$module for each module
  def writeConfigVarsTf: IO[Unit] = for {
    modules <- tfParser.getModuleList
    varStanzas = modules.map(moduleName => mkDef(s"config_$moduleName"))
    varFile = ConstPaths.TF_MODULES_DIR / "config_vars.tf"
    _ <- IO(os.write.over(varFile, varStanzas.mkString("\n")))
  } yield ()

  // Creates a HCL variable stanza defining the passed varName as a map(any)
  private def mkDef(varName: String): String =
    s"""
      |variable "$varName" {
      |  type = map(any)
      |  default = {}
      |}
      |""".stripMargin

  /**
   * Creates a main.tf file that loads each of the specified terraform modules
   * @param modules List of module directory names to load
   * @return The contents of the main.tf file
   */
  private def mkMainTf(modules: List[String]): IO[String] =
    modules
      .map { module =>
        tfParser.getHclVarList(module).map { varList =>
          // List of key value pairs to set in the module
          val vars = List(
            // These vars are essential to the module
            List(
              "source" -> s""""./$module"""",
              "enable" -> s"""lookup(var.feature_flags, "$module", false)""",
              "config" -> s"var.config_$module"
            ),
            // These vars are defined at the root level in daemonutil and passed to each module
            SHARED_VARS.map { varName =>
              varName -> s"var.$varName"
            },
            // These vars are pulled from the flags.json file and passed to each module
            featureFlags.SHARED_FLAGS.toList.map { varName =>
              varName -> s"""lookup(var.feature_flags, "$varName", false)"""
            }
          ).flatten.filter {
            // If the variables.tf file of the module doesn't have the variable defined, don't pass it to the module
            case (varName, _) => varList.contains(varName) || varName == "source"
          }
          s"""
           |module "$module" {
           |  ${vars.map { case (k, v) => s"$k = $v" }.mkString("\n  ")}
           |}
           |""".stripMargin
        }
      }
      .sequence
      .map(_.mkString("\n"))
}
