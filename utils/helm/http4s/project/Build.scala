package com.verizon.helm.http4s
import sbt._
import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

object Versions {
  val http4sOrg = "org.http4s"
  val http4sVersion = "0.18.11"
  val dockeritVersion = "0.9.0"

  val scalaTestVersion = "3.0.5"
  val scalaCheckVersion = "1.13.4"
}

object Dependencies {
  import Versions._
  val jvmLibraryDependencies = Def.settings(
    libraryDependencies ++= Seq(
      "biz.enef" %% "slogging" % "0.6.1",
      "io.verizon.journal" %% "core" % "3.0.18",
      http4sOrg %% "http4s-blaze-client" % http4sVersion,
      http4sOrg %% "http4s-argonaut" % http4sVersion,
      "com.whisk" %% "docker-testkit-scalatest" % dockeritVersion % "test",
      "com.whisk" %% "docker-testkit-impl-docker-java" % dockeritVersion % "test",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test"
    ))
  val jsLibraryDependencies = Def.settings(
    libraryDependencies ++= Seq(
      //      http4sOrg %%% "http4s-blaze-client" % http4sVersion,
      //      http4sOrg %%% "http4s-argonaut" % http4sVersion
      "org.scala-js" %%% "scalajs-dom" % "0.9.2",
      "biz.enef" %%% "slogging" % "0.6.1",
      "fr.hmil" %%% "roshttp" % "2.1.0",
      "org.scalaz" %%% "scalaz-core" % "7.2.26",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test",
      "org.scalacheck" %%% "scalacheck" % scalaCheckVersion % "test",
      "com.whisk" %% "docker-testkit-scalatest" % dockeritVersion % "test",
      "com.whisk" %% "docker-testkit-impl-docker-java" % dockeritVersion % "test",
      http4sOrg %% "http4s-blaze-client" % http4sVersion % "test",
      http4sOrg %% "http4s-argonaut" % http4sVersion % "test",
    ))
  val jsCompilerOptions = Def.settings(
    scalacOptions in Compile := Seq(
      "-deprecation",
      "-encoding",
      "utf-8",
      "-explaintypes",
      "-feature",
      "-language:existentials",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xcheckinit",
      //"-Xfatal-warnings",
      "-Xfuture",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-inaccessible",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xlint:constant",
      "-Ywarn-extra-implicit",
      "-Xlint:adapted-args",
      "-Xlint:by-name-right-associative",
      "-Xlint:delayedinit-select",
      "-Xlint:doc-detached",
      "-Xlint:inaccessible",
      "-Xlint:infer-any",
      "-Xlint:missing-interpolator",
      "-Xlint:nullary-override",
      "-Xlint:nullary-unit",
      "-Xlint:option-implicit",
      "-Xlint:package-object-classes",
      "-Xlint:poly-implicit-overload",
      "-Xlint:private-shadow",
      "-Xlint:stars-align",
      "-Xlint:type-parameter-shadow",
      "-Xlint:unsound-match",
      "-Ywarn-infer-any",
      "-Ypartial-unification"
    )
  )

  val project = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .withoutSuffixFor(JVMPlatform)
    .jsSettings(jsLibraryDependencies, jsCompilerOptions)
    .jvmSettings(jvmLibraryDependencies)
}
