package com.radix.timberland

import sbt._
import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtcrossproject.CrossProject
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager._
import universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.MappingsHelper._

import com.radix.utils.helm.{Dependencies => HelmDeps}
import com.radix.utils.prism.{Dependencies => PrismDeps}
import com.radix.shared.{Dependencies => SharedDeps}

object Versions {
  val scala                = "2.12.8"
  val cats                 = "1.6.0"
  val catseffect           = "1.2.0"
  val ammoniteops          = "1.6.3"
  val optparse_applicative = "ar"
  val circe                = "0.10.0"
  val scribe               = "2.7.3"
  val timberland_version   = "0.1"
}
object Dependencies {
  import Versions._
  val commonSettings = Def.settings(
    Seq(
      organization := "com.radix",
      scalaVersion := scala,
      version := timberland_version
    ),
    mainClass in Compile := Some("com.radix.timberland.runner"),
    scalacOptions += "-Ypartial-unification",
    resolvers += Resolver.sonatypeRepo("releases"),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
  )
  val jvmLibraryDependencies = Def.settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi"        %% "ammonite-ops"         % "1.6.3",
      "org.typelevel"      %% "cats-effect"          % "1.2.0",
      "org.typelevel"      %% "cats-core"            % "1.6.0",
      "io.circe"           %% "circe-core"           % "0.10.0",
      "io.circe"           %% "circe-generic"        % "0.10.0",
      "io.circe"           %% "circe-parser"         % "0.10.0",
      "com.lihaoyi"        %% "os-lib"               % "0.2.7",
      "com.outr"           %% "scribe"               % "2.7.3",
      "com.github.xuwei-k" %% "optparse-applicative" % "0.8.0",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
    )
  )

  lazy val helm       = HelmDeps.helm in file("./first-party/helm") dependsOn (helmCore, helmHttp4s) aggregate (helmCore, helmHttp4s)
  lazy val helmCore   = HelmDeps.helmcore in file("./first-party/helm/core")
  lazy val helmHttp4s = HelmDeps.helmhttp4s in file("./first-pary/helm/http4s") dependsOn (helmCore) aggregate (helmCore)

  lazy val prismProject  = PrismDeps.prism in file("./first-party/prism")
  lazy val sharedProject = SharedDeps.shared in file("./first-party/shared/shared")

  val timberland =
    crossProject(JVMPlatform)
      .withoutSuffixFor(JVMPlatform)
      .settings(commonSettings)
      .jvmConfigure(_.enablePlugins(JavaAppPackaging))
      .jvmSettings(
        jvmLibraryDependencies,
        mappings in Universal := {
          val universalMappings = (mappings in Universal).value
          universalMappings
            .foldLeft(Set.empty[(java.io.File, String)]) {
              case (accum, next) =>
                if ((next._2.endsWith(".so") && accum.map(_._2).contains(next._2)) || (next._2
                      .endsWith(".jar") && accum.map(_._2).contains(next._2))) accum
              else accum + next
          } toSeq
        },
//        enablePlugins(JavaAppPackaging),
        maintainer := "Dhasharath Shrivathsa <dhash@radix.bio>",
        packageSummary := "The swiss army knife of the radix toolchain",
        packageDescription := """Timberland contains a toolchain utilities like installing dev dependencies,
|  packaging scripts for CI/CD, and runtime initialization.
""".stripMargin,
        debianPackageDependencies := Seq("openjdk-8-jdk", "git", "ansible"),
        executableScriptName := "timberland",
//        javaOptions in Universal ++= Seq(
//          // -J params will be added as jvm parameters
//          "-J-Xmx512m",
//          "-J-Xms256m",
//          "-java-home ${app_home}/../jre"
//        ),
//        mappings in Universal ++= {
//          val jresDir = new java.io.File(System.getProperties().getProperty("java.home"))
//          println("jresdir" + jresDir)
//          val res = directory(jresDir).map( j =>
//            j._1 -> j._2.replace(jresDir.toPath.toAbsolutePath.toString, "jre")
//          )
//          println(res)
//          res
//        }
      )
}
