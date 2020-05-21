package com.radix.timberland

import io.circe.{Parser => _, _}
import matryoshka.data.Fix

import java.io.File
import scala.io.StdIn.readLine

import scala.concurrent.duration._
import ammonite.ops._
import cats.effect.IO
import cats.implicits._
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.runtime.{Mock, Run}
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.Parser
import scalaz.syntax.apply._
import util.Util

/**
 * radix|- dev|- ci|
 * |     |    |- compile
 * |     |    |- test
 * |     |- native <RADIX_MONOREPO_DIR>
 * |     |- install <RADIX_MONOREPO_DIR>
 * |- runtime|
 * |- start [debug] [dry-run] [force-bind-ip] [dev] [vault] [es] [core] [service-port] [registry-listener-port] [no-restart] [username] [password]
 * |- stop
 * |- trace
 * |- nuke
 * |- start_nomad
 * |- configure| // this all relies on the runtime being active
 * |           |- add|
 * |           |     |- sensor|
 * |           |     |        |- elemental_machines|
 * |           |     |                             |- api_key <api_key>
 * |           |     |                             |- sensor <uuid>
 * |           |     |- robot|
 * |           |             |- opentrons <ip> <node_hostname>
 * |           |- remove <uuid>
 * |           |- move <uuid> <offset> [new_parent_uuid]
 * |- oauth|
 * |- google-sheets
 *
 *
 */

sealed trait RadixCMD

sealed trait Dev extends RadixCMD

sealed trait CI extends Dev

case class Compile(target: Option[String], dir: Option[String]) extends CI

case class Test(targer: Option[String], dir: Option[String]) extends CI

case object Pass extends CI

object DevList extends CI


case class InstallDeps(dir: Option[String]) extends Dev

sealed trait Runtime extends RadixCMD

//case class Dry(run: Runtime) extends Runtime
sealed trait Local extends Runtime

case class Start(
                  dummy: Boolean = false,
                  loglevel: scribe.Level = scribe.Level.Debug,
                  bindIP: Option[String] = None,
                  consulSeeds: Option[String] = None,
                  dev: Boolean = false,
                  core: Boolean = false,
                  vault: Boolean = false,
                  es: Boolean = false,
                  yugabyte: Boolean = false,
                  retool: Boolean = false,
                  elemental: Boolean = false,
                  servicePort: Int = 9092,
                  registryListenerPort: Int = 8081,
                  norestart: Boolean = false,
                  username: Option[String] = None,
                  password: Option[String] = None,
                  upstreamAccessKey: Option[String] = None,
                  upstreamSecretKey: Option[String] = None,
                ) extends Local

case object Stop extends Local

case object Nuke extends Local

case object StartNomad extends Local

sealed trait DNS extends Runtime

case class DNSUp(service: Option[String], bindIP: Option[String]) extends DNS

case class DNSDown(service: Option[String], bindIP: Option[String]) extends DNS

sealed trait Prism extends RadixCMD

case object PList extends Prism

case class PPath(path: String) extends Prism

sealed trait Oauth extends RadixCMD

case object GoogleSheets extends Oauth

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

  val bindepinstall = subparser[Dev](command("install", info(optional(strOption(long("dir"))).map(InstallDeps(_)), progDesc("install the native dependencies required to build radix"))))
  val dev = subparser[Dev](
    command("dev", info(ci.weaken[Dev] <|> bindepinstall, progDesc("developer tools for radix"))))

  val runtime = subparser[Runtime](
    command(
      "runtime",
      info(
        subparser[Runtime](
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
                      case Some("info") => exist.copy(loglevel = scribe.Level.Info)
                      case Some("trace") => exist.copy(loglevel = scribe.Level.Trace)
                      case Some(flag) => throw new IllegalArgumentException(s"$flag isn't a valid loglevel")
                      case None => exist
                    }
                }) <*> optional(strOption(long("force-bind-ip"),
                help("force services to use the specified subnet and IP (in form \"192.168.1.5\")")))
                .map(subnet => { exist: Start =>
                  subnet match {
                    case str@Some(_) => exist.copy(bindIP = str)
                    case None => exist
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
                }) <*> optional(switch(long("dev"), help("force services to start in development mode"))).map(dev => { exist: Start =>
                dev match {
                  case bool@Some(true) => exist.copy(dev = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("no-restart"), help("Don't start/restart nomad and consul"))).map(norestart => { exist: Start =>
                norestart match {
                  case bool@Some(true) => exist.copy(norestart = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("core"), help("start core services"))).map(core => { exist: Start =>
                core match {
                  case bool@Some(true) => exist.copy(core = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("vault"), help("start vault only"))).map(vault => { exist: Start =>
                vault match {
                  case bool@Some(true) => exist.copy(vault = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("es"), help("start elasticsearch only"))).map(es => { exist: Start =>
                es match {
                  case bool@Some(true) => exist.copy(es = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("yugabyte"), help("start yugabyte only"))).map(yugabyte => { exist: Start =>
                yugabyte match {
                  case bool@Some(true) => exist.copy(yugabyte = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("retool"), help("start retool only"))).map(retool => { exist: Start =>
                retool match {
                  case bool@Some(true) => exist.copy(retool = bool.get)
                  case _ => exist
                }
              }) <*> optional(switch(long("elemental"), help("start elemental only"))).map(elemental => { exist: Start =>
                elemental match {
                  case bool@Some(true) => exist.copy(elemental = bool.get)
                  case _ => exist
                }
              }) <*> optional(
                intOption(long("service-port"),
                  help("Kafka service port")))
                .map(sp => { exist: Start =>
                  sp match {
                    case str@Some(_) => exist.copy(servicePort = str.get)
                    case None => exist
                  }
                }) <*> optional(
                intOption(long("registry-listener-port"),
                  help("Kafka registry listener port")))
                .map(rlp => { exist: Start =>
                  rlp match {
                    case str@Some(_) => exist.copy(registryListenerPort = rlp.get)
                    case None => exist
                  }
                }) <*> optional(
                strOption(long("username"),
                  help("elemental user name")))
                .map(username => { exist: Start =>
                  username match {
                    case Some(_) => exist.copy(username = username)
                    case None => exist
                  }
                }) <*> optional(
                strOption(long("password"),
                  help("elemental password")))
                .map(password => { exist: Start =>
                  password match {
                    case Some(_) => exist.copy(password = password)
                    case None => exist
                  }
                })
                <*> optional(
                strOption(long("upstreamAccessKey"),
                  help("S3 access key")))
                .map(upstreamAccessKey => { exist: Start =>
                  upstreamAccessKey match {
                    case Some(_) => exist.copy(upstreamAccessKey = upstreamAccessKey)
                    case None => exist
                  }
                })
                <*> optional(
                strOption(long("upstreamSecretKey"),
                  help("S3 secret key")))
                .map(upstreamSecretKey => { exist: Start =>
                  upstreamSecretKey match {
                    case Some(_) => exist.copy(upstreamSecretKey = upstreamSecretKey)
                    case None => exist
                  }
                }),
              progDesc("start the radix core services on the current system")
            )
          ),
          command("stop", info(pure(Stop), progDesc("stop services across all nodes"))),
          command("nuke", info(pure(Nuke), progDesc("remove radix core services from the node"))),
          command("start_nomad", info(pure(StartNomad), progDesc("start a nomad job"))),
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

  val oauth = subparser[Oauth](
    command("oauth", info(subparser[Oauth](command("google-sheets", info(pure(GoogleSheets),
      progDesc("set up a google sheets token")))))
    ))

  val res: Parser[RadixCMD] = dev.weaken[RadixCMD] <|> runtime.weaken[RadixCMD] <|>
    prism.weaken[RadixCMD] <|> oauth.weaken[RadixCMD]

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
      case x86 if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains("x86") => "amd64"
      case _ => "arm"
    }

    val persistentdir = "/opt/radix/timberland/" // TODO make configurable
    val systemdDir = "/opt/radix/systemd/"
    val appdatadir = new File(persistentdir)
    val consul = new File(persistentdir + "/consul/consul")
    val nomad = new File(persistentdir + "/nomad/nomad")
    //    val timberlandJar = new File(persistentdir + "/timberland.jar")
    val nginx = new File(persistentdir + "/nginx/")
    nginx.mkdirs
    val minio = new File("/opt/radix/minio_data/")
    minio.mkdirs
    val minio_bucket = new File("/opt/radix/minio_data/userdata")
    minio_bucket.mkdirs
    nomad.getParentFile.mkdirs()
    consul.getParentFile.mkdirs()

    object Dev {

      case class Opt(dir: os.RelPath, flags: Seq[os.Shellable] = Seq())

      // list of possible targets for `dev compile` and `dev test`
      val targetOpts = Map("compiler" -> Opt("compiler"),
        "runtime" -> Opt("runtime"),
        "pipettelinearizer" -> Opt("algs" / "pipette-linearizer"),
        "frontend" -> Opt("interface", flags = Seq("-J-Xmx4G", "-J-Xss64m", "-J-XX:MaxMetaspaceSize=1G")))

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

        def number: Parser[Double] = """\d+(\.\d*)?""".r ^^ {
          _.toDouble
        }

        def offset1: Parser[Offset] = number ^^ { case n => Offset(Meters(n)) }

        def offset2: Parser[Offset] = (number ~ "," ~ number) ^^ { case s ~ _ ~ t => Offset(Meters(s), Meters(t)) }

        def offset3: Parser[Offset] = (number ~ "," ~ number ~ "," ~ number) ^^ { case s ~ _ ~ t ~ _ ~ u => Offset(Meters(s), Meters(t), Meters(u)) }

        def offset: Parser[Offset] = offset3 | offset2 | offset1 | parens(offset)

        def terminal: Parser[Fix[Container]] = offset ^^ { case offset => Fix(Shim(offset, Seq())) }

        def nonterminal: Parser[Fix[Container]] = offset ~ "/" ~ container ^^ { case offset ~ _ ~ container => Fix(Shim(offset, Seq(container))) }

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
            case InstallDeps(dir) => {
              import sys.process._
              val installDir: String = dir getOrElse (sys.env.get("RADIX_MONOREPO_DIR") getOrElse "pwd".!!.trim)
              scribe.info("installing dependencies")
              val invokeSoftwareProperties =
                Seq("sudo", "apt", "install", "software-properties-common", "-y")
              val addAnsiblePPA =
                Seq("sudo", "apt-add-repository", "ppa:ansible/ansible", "-y")
              val updateAPT =
                Seq("sudo", "apt", "update", "-y")
              val installAnsible =
                Seq("sudo", "apt", "install", "ansible", "-y")
              val invocation =
                Seq("sudo", "ansible-playbook", installDir + "/getdeps.yml", "--extra-vars", "monorepo-dir=" + installDir)
              //scribe.info(s"building compiler with: $invocation")
              os.proc(invokeSoftwareProperties).call()
              os.proc(addAnsiblePPA).call()
              os.proc(updateAPT).call()
              os.proc(installAnsible).call()
              os.proc(invocation).call()
              //invokeSoftwareProperties !
              //addAnsiblePPA !
              //updateAPT !
              //installAnsible !
              //invocation !
            }
          }
        case run: Runtime =>
          run match {
            case local: Local =>
              local match {
                case Nuke => Right(Unit)
                case cmd@Start(dummy, loglevel, bindIP, consulSeedsO, dev, core, vault, es, yugabyte, retool, elemental, servicePort, registryListenerPort, norestart, username, password, upstreamAccessKey, upstreamSecretKey) => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(loglevel))
                    .replace()
                  scribe.info(s"starting runtime with $cmd")
                  import ammonite.ops._
                  var bootstrapExpect: Int = 3
                  if (dev) {
                    bootstrapExpect = 1
                  }

                  var startCore = core
                  var startVault = vault
                  var startEs = es
                  var startRetool = retool
                  var startYugabyte = yugabyte
                  var startElemental = elemental

                  if (!startCore && !startVault && !startEs && !startRetool && !startYugabyte && !startElemental) {
                    startCore = true
                    startVault = true
                    startEs = true
                    startRetool = true
                    startYugabyte = true
                    startElemental = false
                  }

                  scribe.info(s"******* CORE: $startCore")

                  if (dummy) {
                    implicit val host = new Mock.RuntimeNolaunch[IO]
                    Right(
                      println(Run
                        .initializeRuntimeProg[IO](Path(consul.toPath.getParent), Path(nomad.toPath.getParent), bindIP, consulSeedsO, bootstrapExpect)
                        .unsafeRunSync)
                    )
                    System.exit(0)
                  } else {
                    scribe.info("Creating weave network")
                    val prog = for {
                      _ <- IO(os.proc("/usr/bin/sudo /sbin/sysctl -w vm.max_map_count=262144".split(' ')).spawn())
                      pluginList <- IO(os.proc("/usr/bin/docker plugin ls".split(' ')).call(cwd = os.root, check = false))
                      _ <- pluginList.out.string.contains("weaveworks/net-plugin:2.6.0") match {
                        case true => {
                          IO(os.proc("/usr/bin/docker network create --driver=weaveworks/net-plugin:2.6.0 --attachable weave".split(' ')).call(cwd = os.root, stdout = os.Inherit, check = false))
                          IO.pure(scribe.info("Weave network exists or was created"))
                        }
                        case false => IO.pure(scribe.info("Weave plugin not installed. Skipping creation of weave network."))
                      }

                    } yield ()

                    implicit val host = new Run.RuntimeServicesExec[IO]
                    val res = if (!norestart) {

                        prog *> Run
                          .initializeRuntimeProg[IO](Path(consul.toPath.getParent), Path(nomad.toPath.getParent), bindIP, consulSeedsO, bootstrapExpect)
                    } else prog
                    scribe.info("Launching daemons")
                    scribe.info(s"***********DAEMON SIZE${bootstrapExpect}***************")

                    val waitForNomadAndApplyTerraformAndMaybeUnsealVault = if(startVault) {
                      res *>
                        daemonutil.waitForDNS("nomad.service.consul", 2.minutes) *>
                        daemonutil.runTerraform(integrationTest = false, dev = dev, core = startCore, yugabyteStart = startYugabyte, vaultStart = startVault, esStart = startEs, retoolStart = startRetool, elementalStart = startElemental, upstreamAccessKey, upstreamSecretKey) *>
                        daemonutil.waitForQuorum(core = startCore, yugabyteStart = startYugabyte, vaultStart = startVault, esStart = startEs, retoolStart = startRetool, elementalStart = startElemental) *>
                        daemonutil.unsealVault(dev = dev)
                    } else {
                      res *>
                        daemonutil.waitForDNS("nomad.service.consul", 2.minutes) *>
                        daemonutil.runTerraform(integrationTest = false, dev = dev, core = startCore, yugabyteStart = startYugabyte, vaultStart = startVault, esStart = startEs, retoolStart = startRetool, elementalStart = startElemental, upstreamAccessKey, upstreamSecretKey) *>
                        daemonutil.waitForQuorum(core = startCore, yugabyteStart = startYugabyte, vaultStart = startVault, esStart = startEs, retoolStart = startRetool, elementalStart = startElemental)
                    }

                    waitForNomadAndApplyTerraformAndMaybeUnsealVault.unsafeRunSync
                    sys.exit(0)
                  }
                }
                case Stop => {
                  //Right(println(daemonutil.stopAllServices.unsafeRunSync))
                  //scribe.info("All services stopped")
                  //sys.exit(0)
                  scribe.error("Stop command not implemented!")
                }
                case StartNomad => Right(Unit)
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
                    case "dnsmasq" => launch.dns.upDnsmasq(ip, Util.getIfFromIP(ip)).unsafeRunSync()
                    case "systemd" => launch.dns.upSystemd(ip).unsafeRunSync()
                    case "iptables" => launch.dns.upIptables(ip).unsafeRunSync()
                    case unrecognized =>
                      scribe.info("unrecognized dns service: " + unrecognized)
                  }
                case DNSDown(service, bindIP) =>
                  val ip = bindIP.getOrElse(Util.getDefaultGateway)
                  service.getOrElse(launch.dns.identifyDNS) match {
                    case "dnsmasq" => launch.dns.downDnsmasq(ip).unsafeRunSync()
                    case "systemd" => launch.dns.downSystemd(ip).unsafeRunSync()
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

        case oauth: Oauth =>
          oauth match {
            case GoogleSheets => {
              val credentials = for {
                creds <- OAuthController.creds
                _ <- IO(scribe.info("Credentials successfully stored!"))
              } yield creds
              credentials.unsafeRunSync()
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
