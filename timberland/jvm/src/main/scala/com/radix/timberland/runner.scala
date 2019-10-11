package com.radix.timberland

import cats._
import cats.data._
import cats.effect.{Clock, ContextShift, Effect, ExitCase, IO, Timer}
import cats.implicits._
import ammonite._
import ammonite.ops._
import io.circe.{Parser => _, _}
import io.circe.generic.auto._
//import io.circe.parser._
import matryoshka.data.Fix

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.Symbol
import java.net.{InetAddress, NetworkInterface, Socket}
import java.nio.file.{Files, StandardCopyOption}
import java.io.{File, FileInputStream}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import runtime.{Download, Installer, Mock, Run}
import dev._
import optparse_applicative._
import optparse_applicative.types.{BindP, NilP, Parser}
import scalaz.syntax.apply._
import util.Util

import sun.misc.{Signal, SignalHandler}

/**
  * radix|- dev|- ci|
  *      |     |    |- compile
  *      |     |    |- test
  *      |     |- native
  *      |     |- install
  *      |- runtime|
  *                |- install
  *                |- start [debug] [dry-run] [force-bind-ip]
  *                |- trace
  *                |- nuke
  *                |- start_nomad
  *                |- configure| // this all relies on the runtime being active
  *                |           |- add|
  *                |           |     |- sensor|
  *                |           |     |        |- elemental_machines|
  *                |           |     |                             |- api_key <api_key>
  *                |           |     |                             |- sensor <uuid>
  *                |           |     |- robot|
  *                |           |             |- opentrons <ip> <node_hostname>
  *                |           |- remove <uuid>
  *                |           |- move <uuid> <offset> [new_parent_uuid]
  *                |- launch|
  *                         |- zookeeper <tname...>
  *                         |- kafka <tname...>
  *
  *
  *
  *
  */

sealed trait RadixCMD
sealed trait Dev                                                extends RadixCMD
sealed trait CI                                                 extends Dev
case class Compile(target: Option[String], dir: Option[String]) extends CI
case class Test(targer: Option[String], dir: Option[String])    extends CI
case object Pass                                                extends CI
object DevList                                                  extends CI
case object Native                                              extends Dev
case object InstallDeps                                         extends Dev
sealed trait Runtime                                            extends RadixCMD
//case class Dry(run: Runtime) extends Runtime
sealed trait Launch         extends Runtime
case object LaunchZookeeper extends Launch
case object LaunchKafka     extends Launch
sealed trait Local          extends Runtime
case object Install         extends Local
case class Start(
    dummy: Boolean = false,
    loglevel: scribe.Level = scribe.Level.Debug,
    bindIP: Option[String] = None,
    consulSeeds: Option[String] = None
) extends Local
case object Nuke                                                    extends Local
case object StartNomad                                              extends Local
sealed trait DNS                                                    extends Runtime
case class DNSUp(service: Option[String], bindIP: Option[String])   extends DNS
case class DNSDown(service: Option[String], bindIP: Option[String]) extends DNS
sealed trait Prism                                                  extends RadixCMD
case object PList                                                   extends Prism
case class PPath(path: String)                                      extends Prism

case class ScriptHead(cmd: RadixCMD)

object runner {

  implicit class Weakener[F[_], A](fa: F[A])(implicit F: scalaz.Functor[F]) {
    def weaken[B](implicit ev: A <:< B): F[B] = fa.map(identity(_))
  }
  val ci = subparser[CI](
    command(
      "ci",
      info(
        subparser[CI](command("compile", info(^(optional(strArgument(metavar("TARGET"))),
                                                optional(strOption(long("dir"))))(Compile),
                                              progDesc("compile all the projects"))),
                      command("test", info(^(optional(strArgument(metavar("TARGET"))),
                                            optional(strOption(long("dir"))))(Test),
                                           progDesc("test all the projects"))),
                      command("pass", info(pure(Pass), progDesc("test all the projects"))),
                      command("list", info(pure(DevList), progDesc("list available build targets")))),
        progDesc("continuous integration tooling for radix")
      )
    ))

  val native =
    subparser[Dev](command("native", info(pure(Native), progDesc("build all the native dependencies of radix core"))))
  val bindepinstall = subparser[Dev](command("install", info(pure(InstallDeps), progDesc("install the native dependencies required to build radix"))))
  val dev = subparser[Dev](
    command("dev", info(ci.weaken[Dev] <|> native <|> bindepinstall, progDesc("developer tools for radix"))))

  val runtime = subparser[Runtime](
    command(
      "runtime",
      info(
        subparser[Runtime](
          command("install", info(pure(Install), progDesc("install radix core services on your system"))),
          command(
            "start",
            info(
              switch(long("dry-run"), help("whether to run the mock intepreter")).map({ flag =>
                Start(flag)
              }) <*>
                optional(strOption(long("debug"), help("what debug level the service should run at"))).map(debug => {
                  exist: Start =>
                    debug match {
                      case Some("debug") => exist.copy(loglevel = scribe.Level.Debug)
                      case Some("error") => exist.copy(loglevel = scribe.Level.Error)
                      case Some("info")  => exist.copy(loglevel = scribe.Level.Info)
                      case Some("trace") => exist.copy(loglevel = scribe.Level.Trace)
                      case Some(flag)    => throw new IllegalArgumentException(s"$flag isn't a valid loglevel")
                      case None          => exist
                    }
                }) <*> optional(
                strOption(long("force-bind-ip"),
                          help("force services to use the specified subnet and IP (in form \"192.168.1.5\")")))
                .map(subnet => { exist: Start =>
                  subnet match {
                    case str @ Some(_) => exist.copy(bindIP = str)
                    case None          => exist
                  }
                }) <*> optional(
                strOption(long("consul-seeds"),
                  help("comma separated list of seed nodes for consul (maps to retry_join in consul.json)")))
                  .map(seeds => {
                    exist: Start =>
                      seeds match {
                        case list@Some(_) => exist.copy(consulSeeds = list)
                        case None => exist
                      }
                  }),
              progDesc("start the radix core services on the current system")
            )
          ),
          command("nuke", info(pure(Nuke), progDesc("remove radix core services from the node"))),
          command("start_nomad", info(pure(StartNomad), progDesc("start a nomad job"))),
          command(
            "launch",
            info(subparser[Launch](
              command("zookeeper",
                      info(pure(LaunchZookeeper), progDesc("launch zookeeper at runtime, launched from nomad"))),
              command("kafka", info(pure(LaunchKafka), progDesc("launch kafka at runtime, launched from nomad")))
            ).weaken[Runtime])
          ),
          command(
            "dns",
            info(subparser[DNS](
              command("up",
                      info(^(optional(strOption(long("service"))), optional(strOption(long("force-bind-ip"))))(DNSUp),
                           progDesc("inject consul into dns resolution"))),
              command(
                "down",
                info(^(optional(strOption(long("service"))), optional(strOption(long("force-bind-ip"))))(DNSDown),
                     progDesc("deinject consul from dns resolution"))
              ),
            ).weaken[Runtime])
          )
        ),
        progDesc("radix runtime component")
      )
    )
  ) <*> helper

  val prism = subparser[Prism](
    command("prism", info(subparser[Prism](
      command("list", info(pure(PList), progDesc("list prism tree"))),
      command("path", info(strArgument(metavar("PATH")).map(PPath),
                           progDesc("idk make a path or s/t")))))))

  val res: Parser[RadixCMD] = dev.weaken[RadixCMD] <|> runtime.weaken[RadixCMD] <|> prism.weaken[RadixCMD]

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
      case mac if mac.toLowerCase.contains("mac")       => "darwin"
      case linux if linux.toLowerCase.contains("linux") => "linux"
    }
    val arch = System.getProperty("os.arch") match {
      case x86 if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains("x86") => "amd64"
      case _                                                                           => "arm"
    }

    val persistentdir = "/tmp/radix/timberland/" // TODO make configurable
    val appdatadir    = new File(persistentdir)
    val consul        = new File(persistentdir + "/consul/consul")
    val nomad         = new File(persistentdir + "/nomad/nomad")
    nomad.getParentFile.mkdirs()
    consul.getParentFile.mkdirs()

    object Dev {
      case class Opt(dir: os.RelPath, flags: Seq[os.Shellable] = Seq())
      // list of possible targets for `dev compile` and `dev test`
      val targetOpts = Map("compiler" -> Opt("compiler"),
                           "runtime" -> Opt("runtime"),
                           "pipettelinearizer" -> Opt("algs" / "pipette-linearizer"),
                           "frontend" -> Opt("interface", flags=Seq("-J-Xmx4G", "-J-Xss64m", "-J-XX:MaxMetaspaceSize=1G")))

      sealed trait Action
      case object Compile extends Action
      case object Test extends Action

      def callSbt(action: Action, target: String, dir: Option[String]) = {
        println(dir)
        val invocation: Seq[os.Shellable] = Seq("sbt", action match {
          case Compile => "compile"
          case Test => "test"
        })
//        val dir: String = optionalDir getOrElse (sys.env.get("RADIX_MONOREPO_DIR") getOrElse "pwd".!!.trim)

        os.proc(invocation ++ targetOpts(target).flags: _*)
          .call(cwd = (dir map { dir: String => os.Path(dir) } getOrElse os.pwd) / targetOpts(target).dir,
                stdout = os.Inherit, stderr = os.Inherit)
      }

      def compile(target: String, dir: Option[String]) = {
        scribe.info(s"building $target")
        callSbt(Compile, target, dir)
      }

      def test(target: String, dir: Option[String]) = {
        scribe.info(s"testing $target")
        callSbt(Test, target, dir)
      }
    }

    // helper object containing parsers for command-line representation of prism containers and paths
    object PrismParse {
      import scala.util.parsing.combinator._
      import scala.util.parsing.input.CharSequenceReader
      import com.radix.shared.util.prism._
      import squants.space._

      object PathParser extends RegexParsers {
        def parens[A](p: Parser[A]): Parser[A] = "(" ~ p ~ ")" ^^ { case _ ~ p ~ _ => p }
        def number: Parser[Double] = """\d+(\.\d*)?""".r ^^ { _.toDouble }

        def offset1: Parser[Offset] = number ^^ { case n => Offset(Meters(n)) }
        def offset2: Parser[Offset] = (number ~ "," ~ number) ^^
          { case s ~ _ ~ t => Offset(Meters(s), Meters(t)) }
        def offset3: Parser[Offset] = (number ~ "," ~ number ~ "," ~ number) ^^
          { case s ~ _ ~ t ~ _ ~ u => Offset(Meters(s), Meters(t), Meters(u)) }
        def offset: Parser[Offset] = offset3 | offset2 | offset1 | parens(offset)

        def terminal: Parser[Fix[Container]] = offset ^^ { case offset => Fix(Shim(offset, Seq())) }
        def nonterminal: Parser[Fix[Container]] = offset ~ "/" ~ container ^^
          { case offset ~ _ ~ container => Fix(Shim(offset, Seq(container))) }
        def container: Parser[Fix[Container]] = nonterminal | terminal
      }
    }

    def cmdEval(cmd: RadixCMD): Unit = {
      cmd match {
        case dev: Dev =>
          dev match {
            case ci: CI =>
              ci match {
                case Compile(target, dir) => target match {
                  case None | Some("all") => Dev.targetOpts.keys.map(Dev.compile(_, dir))
                  case Some(target) => Dev.compile(target, dir)
                }
                case Test(target, dir) => target match {
                  case None | Some("all") => Dev.targetOpts.keys.map(Dev.test(_, dir))
                  case Some(target) => Dev.test(target, dir)
                }
                case Pass =>
                 ???
                case DevList => Dev.targetOpts.keys.map(scribe.info(_))
              }
            case Native => {
              implicit val cs: ContextShift[IO] = IO.contextShift(global)
              val NUM_CORES                     = Runtime.getRuntime().availableProcessors()

              val builds = (Build.or_tools(NUM_CORES), Build.Z3).parMapN { (_, _) =>
                ()
              }
              val prog = for {
                _ <- Build.submodules(NUM_CORES)
                _ <- builds
              } yield ()

              prog.unsafeRunSync()
              sys.exit(0)
            }
            case InstallDeps => {
              import sys.process._
              scribe.info("installing dependencies")
              val invocation =
                Seq("sudo", "ansible-playbook", "../getdeps.yml")
              //scribe.info(s"building compiler with: $invocation")
              invocation !
            }
          }
        case run: Runtime =>
          run match {
            case local: Local =>
              local match {
                case Install => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  //TODO swap out with nicer install detection, possibly via the shavtable stuff
                  if (!consul.exists() && !nomad.exists()) {
                    val dl =
                      Download.downloadConsulAndNomad[IO]("1.4.4", "0.9.0", List(osname, arch), List(osname, arch))
                    val resourceMover = new Installer.MoveFromJVMResources[IO]()
                    val prog = for {
                      file <- dl
                      _    <- IO(scribe.info("download complete, canonicalizing directories"))
                      _ <- for {
                        _ <- Util.nioCopyFile(file._1._1, consul)
                        _ <- Util.nioCopyFile(file._2._1, nomad)
                        _ <- IO {
                          consul.setExecutable(true)
                          nomad.setExecutable(true)
                        }
                      } yield ()
                      _ <- resourceMover.fncopy(Path("/consul/consul.json"), Path(consul.getParentFile.toPath))
                      _ <- resourceMover.fncopy(Path("/nomad/config"), Path(nomad.getParentFile.toPath))

                      _ <- resourceMover.fncopy(Path("/systemd/consul.service"), Path("/etc/systemd/system"))
                      _ <- resourceMover.fncopy(Path("/systemd/nomad.service"), Path("/etc/systemd/system"))

                      hookDestinationDir = "/etc/networkd-dispatcher/routable.d/"

                      _ <- resourceMover.fncopy(Path("/systemd/10-radix-consul"), Path(hookDestinationDir))
                      _ <- resourceMover.fncopy(Path("/systemd/10-radix-nomad"), Path(hookDestinationDir))

                      consulHookFile = new File(hookDestinationDir + "10-radix-consul")
                      nomadHookFile = new File(hookDestinationDir + "10-radix-nomad")

                      _ <- IO {
                        consulHookFile.setExecutable(true)
                        nomadHookFile.setExecutable(true)
                      }

                      _ <- IO(os.proc("/usr/bin/sudo /bin/systemctl daemon-reload".split(' ')).spawn())
                      _ <- IO(os.proc("/usr/bin/docker plugin install weaveworks/net-plugin:latest_release".split(' ')).call(cwd = os.root, stdin = "y\n", stdout = os.Inherit, check = false))
                    } yield ()
                    prog.unsafeRunSync()

                    scribe.info("install complete!")
                    sys.exit(0)
                  } else {
                    scribe.warn(s"files in $persistentdir already exists, not installing!")
                    sys.exit(1)
                  }

                }
                case Nuke => Right(Unit)
                case cmd @ Start(dummy, loglevel, bindIP, consulSeedsO) => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(loglevel))
                    .replace()
                  scribe.info(s"starting runtime with $cmd")
                  import ammonite.ops._
                  if (dummy) {
                    implicit val host = new Mock.RuntimeNolaunch[IO]
                    Right(
                      println(Run
                        .initializeRuntimeProg[IO](Path(consul.toPath.getParent), Path(nomad.toPath.getParent), bindIP, consulSeedsO)
                        .unsafeRunSync)
                    )
                    System.exit(0)
                  } else {
                    implicit val host = new Run.RuntimeServicesExec[IO]
                    Right(
                      println(Run
                        .initializeRuntimeProg[IO](Path(consul.toPath.getParent), Path(nomad.toPath.getParent), bindIP, consulSeedsO)
                        .unsafeRunSync)
                    )
                  }
                }
                case StartNomad => Right(Unit)
              }
            case launcher: Launch =>
              launcher match {
                case LaunchZookeeper => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  scribe.trace("launching zookeeper (to be run inside docker container)")
                  val copier = new Installer.MoveFromJVMResources[IO]

                  val prog = for {
                    _ <- IO(scribe.debug("starting file copy..."))
                    _ <- copier.fncopy(Path("/nomad/config/zookeeper/config"), Path("/conf"))
                    _ <- IO(scribe.debug("finished file copy"))
                    _ <- IO(scribe.debug("starting zookeeper..."))
                    zk <- launch.zookeeper
                      .startZookeeper(Path("/local/conf/zoo_servers"),
                                      Path("/conf/zoo.cfg"),
                                      Path("/conf/zoo_replicated.cfg.dynamic"))
                      .run(launch.zookeeper.NoMinQuorum)
                    _ <- IO(scribe.debug("zookeeper started!"))
                    _ <- IO.never
                  } yield ()
                  prog.unsafeRunSync()
                }
                case LaunchKafka => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(scribe.Level.Trace))
                    .replace()
                  val prog = for {
                    _ <- launch.kafka.startKafka
                    _ <- IO.never
                  } yield ()
                  prog.unsafeRunSync()

                }
              }
            case dns: DNS => {
              scribe.Logger.root
                .clearHandlers()
                .clearModifiers()
                .withHandler(minimumLevel = Some(scribe.Level.Debug))
                .replace()
              dns match {
                case DNSUp(service, bindIP) =>
                  val ip = bindIP.getOrElse(Util.getDefaultGateway)
                  service.getOrElse(launch.dns.identifyDNS) match {
                    case "dnsmasq"  => launch.dns.upDnsmasq(ip, Util.getIfFromIP(ip)).unsafeRunSync()
                    case "systemd"  => launch.dns.upSystemd(ip).unsafeRunSync()
                    case "iptables" => launch.dns.upIptables(ip).unsafeRunSync()
                    case unrecognized =>
                      scribe.info("unrecognized dns service: " + unrecognized)
                  }
                case DNSDown(service, bindIP) =>
                  val ip = bindIP.getOrElse(Util.getDefaultGateway)
                  service.getOrElse(launch.dns.identifyDNS) match {
                    case "dnsmasq"  => launch.dns.downDnsmasq(ip).unsafeRunSync()
                    case "systemd"  => launch.dns.downSystemd(ip).unsafeRunSync()
                    case "iptables" => launch.dns.downIptables(ip).unsafeRunSync()
                    case unrecognized =>
                      scribe.info("unrecognized dns service: " + unrecognized)
                  }
              }
            }
          }

        case prism: Prism => {
          prism match {
            case PList => {
              // TODO(lily) this should query prism (how?) and display the kd tree in some form
            }
            case PPath(path) => {
              // this just tends parsing logic
              import PrismParse.PathParser
              println(PathParser.parseAll(PathParser.container, path))

              // TODO(lily) we should do something to a) convert the parsed container into an actual
              // path, and b) check that that path is a valid index into the prism
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