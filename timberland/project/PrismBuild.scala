package com.radix.utils.prism
import sbt._
import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtcrossproject.CrossProject
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

import com.radix.shared.{Dependencies => SharedDeps}

object Versions {
  val prismVersion = "0.3.0-SNAPHSOT"
  val scalaVersionNum = "2.12.8"

  val matryoshkaVersion = "0.21.3"
  val squantsVersion = "1.3.0"
  val scalacacheVersion = "0.24.1"
  val scalaUUIDVersion = "0.2.4"
  val spireVersion = "0.16.0"
  val shapelessVersion = "2.3.3"
  val circeVersion = "0.10.0-M1"
}

object Dependencies {
  import Versions._
  val commonSettings = Def.settings(
    Seq(
      organization := "com.radix",
      scalaVersion := scalaVersionNum,
      version := prismVersion
    ))
  val jvmLibraryDependencies = Def.settings(
    libraryDependencies ++= Seq(
      "com.slamdata" %% "matryoshka-core" % matryoshkaVersion,
      "org.typelevel" %% "squants" % squantsVersion,
      "org.typelevel" %% "spire" % spireVersion,
      "com.chuusai" %% "shapeless" % shapelessVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )
  val jvmCompilerOptions = Def.settings(
    scalacOptions ++= Seq(
      "-Ypartial-unification",
      "-explaintypes",
      "-Xexperimental"
    )
  )
  val jsLibraryDependencies = Def.settings(
    libraryDependencies ++= Seq(
      "com.slamdata" %%% "matryoshka-core" % matryoshkaVersion,
      "org.typelevel" %%% "squants" % squantsVersion,
      "org.typelevel" %%% "spire" % spireVersion,
      "com.chuusai" %%% "shapeless" % shapelessVersion,
      "org.scalatest" %%% "scalatest" % "3.0.5" % "test"
    ),
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeVersion)
  )
  val sharedProject = SharedDeps.shared in file("./first-party/shared/shared")

  lazy val prism = crossProject(JVMPlatform, JSPlatform)
    .withoutSuffixFor(JVMPlatform)
    .settings(commonSettings)
    .jvmSettings(jvmLibraryDependencies, jvmCompilerOptions).jsSettings(jsLibraryDependencies, jvmCompilerOptions)

}
