package com.radix.timberland

import io.circe.{Parser => _, _}
import scala.io.StdIn.{readLine}

//import ammonite.ops._
import cats.effect.{ContextShift, IO}
import cats.implicits._ //NECESSARY
import com.radix.timberland.runtime.{Installer}
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.Parser
import scalaz.syntax.apply._ //NECESSARY

import sun.misc.{Signal, SignalHandler}

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * radix|- runtime|
 * |- launch|
 * |- zookeeper <tname...> [dev]
 * |- kafka <tname...> [dev]
 *
 *
 *
 *
 */
sealed trait LauncherCMD

sealed trait LaunchMe extends LauncherCMD

sealed trait Launch extends LaunchMe

case class LaunchZookeeper(dev: Boolean) extends Launch

case class LaunchKafka(dev: Boolean, prefix: String) extends Launch

case class LaunchYugabyte(dev: Boolean, master: Boolean, prefix: String) extends Launch

case class Random() {
  def apply(dev: Boolean, str: String) = {
    println("yep")
  }
}

object launcher {

  implicit class Weakener[F[_], A](fa: F[A])(implicit F: scalaz.Functor[F]) {
    def weaken[B](implicit ev: A <:< B): F[B] = fa.map(identity(_))
  }

  val launcher =
    subparser[LaunchMe](
      command(
        "launch",
        info(
          subparser[Launch](
            command(
              "zookeeper",
              info(
                switch(long("dev"), help("start zookeeper in dev mode")).map({
                  dev =>
                    LaunchZookeeper(dev)
                }),
                progDesc("launch zookeeper at runtime, launched from nomad"))
            ),
            command(
              "kafka",
              info(^(
                switch(long("dev"), help("start kafka in dev mode")),
                strOption(long("prefix"), help("zookeeper's job prefix"), metavar("<PREFIX>")))
              ((dev, prefix) => LaunchKafka(dev, prefix)),
                progDesc("launch kafka at runtime, launched from nomad"))
            ),
            command(
              "yugabyte-master",
              info(^(
                switch(long("dev"), help("start yugabyte master in dev mode")),
                strOption(long("prefix"), help("the prefix of the nomad jobs"), metavar(">PREFIX")))
              ((dev, prefix) => LaunchYugabyte(dev, true, prefix)),
                progDesc("launch yugabyte master at runtime, launched from nomad"))
            ),
            command(
              "yugabyte-tserver",
              info(^(
                switch(long("dev"), help("start yugabyte tserver in dev mode")),
                strOption(long("prefix"), help("the prefix of the nomad jobs"), metavar("<PREFIX>")))
              ((dev, prefix) => LaunchYugabyte(dev, false, prefix)),
              progDesc("launch yugabyte tserver at runtime, launched from nomad"))
            )
          ).weaken[LaunchMe], progDesc("radix Launcher component"))
      )
    ) <*> helper

  val res: Parser[LauncherCMD] = launcher.weaken[LauncherCMD]

  val opts =
    info(res <*> helper,
      progDesc("Print a greeting for TARGET"),
      header("hello - a test for scala-optparse-applicative"))

  var sudopw: Option[String] = None

  def checkSudo: Option[String] = {
    if (sudopw.isEmpty) {
      import sys.process._
      try { // there's not much that can go wrong here but we REALLY don't want to leave echo
        // off on the user's shell if something does break
        Seq("/bin/sh", "-c", "stty -echo < /dev/tty").!
        sudopw = Some(readLine("please enter your sudo password: "))
      } finally {
        Seq("/bin/sh", "-c", "stty echo < /dev/tty").!
      }
    }
    sudopw
  }

  def main(args: Array[String]): Unit = {
    println(s"args: ${args.toList}")
    val osname = System.getProperty("os.name") match {
      case mac if mac.toLowerCase.contains("mac") => "darwin"
      case linux if linux.toLowerCase.contains("linux") => "linux"
    }
    val arch = System.getProperty("os.arch") match {
      case x86
        if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains(
          "x86") =>
        "amd64"
      case _ => "arm"
    }

    def cmdEval(cmd: LauncherCMD): Unit = {
      cmd match {
        case run: LaunchMe =>
          run match {
            case launcher: Launch =>
              launcher match {
                case LaunchZookeeper(dev) => {
                  var minQuorumSize: Int = 3
                  if (dev) {
                    minQuorumSize = 1
                  }
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  scribe.trace(
                    "launching zookeeper (to be run inside docker container)")
                  val copier = new Installer.MoveFromJVMResources[IO]

                  val prog = for {
                    _ <- IO(scribe.debug("starting zookeeper..."))
                    zk <- launch.zookeeper
                      .startZookeeper(os.Path("/local/conf/zoo_servers"),
                        os.Path("/conf/zoo.cfg"),
                        os.Path("/conf/zoo_replicated.cfg.dynamic"),
                        minQuorumSize)
                      .run(launch.zookeeper.NoMinQuorum)
                    _ <- IO(scribe.debug("zookeeper started!"))
                    _ <- IO.never
                  } yield ()
                  prog.unsafeRunSync()
                }

                case LaunchKafka(dev, prefix) => {
                  implicit var minQuorumSize: Int = 3
                  if (dev) {
                    minQuorumSize = 1
                  }
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  val prog = for {
                    _ <- launch.kafka.startKafka(prefix)
                    _ <- IO.never
                  } yield ()
                  prog.unsafeRunSync()

                }

                case LaunchYugabyte(dev, master, prefix) => {
                  implicit var minQuorumSize: Int = 3
                  if (dev) {
                    minQuorumSize = 1
                  }
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  val prog = for {
                    _ <- launch.yugabyte.startYugabyte(master, prefix)
                    _ <- IO.never
                  } yield ()
                  prog.unsafeRunSync()
                }
              }
          }
      }
    }

    try {
      cmdEval(execParser(args, "timberland", opts))
    } catch {
      case os.SubprocessException(result) => sys.exit(result.exitCode)
    }
  }

}
