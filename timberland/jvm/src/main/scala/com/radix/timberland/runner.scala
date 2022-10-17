package com.radix.timberland

import java.io.File

import ammonite.ops._
import cats.effect.IO
import cats.implicits._
import com.radix.timberland.flags.{ConsulFlagsUpdated, FlagsStoredLocally, flagConfig}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.VaultStarter
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.Parser
import scalaz.syntax.apply._

import scala.io.StdIn.readLine

sealed trait RadixCMD

sealed trait Runtime extends RadixCMD

sealed trait Local extends Runtime

case class Start(
                  dummy: Boolean = false,
                  loglevel: scribe.Level = scribe.Level.Debug,
                  bindIP: Option[String] = None,
                  consulSeeds: Option[String] = None,
                  remoteAddress: Option[String] = None,
                  servicePort: Int = 9092,
                  accessToken: Option[String] = None,
                  registryListenerPort: Int = 8081,
                  prefix: Option[String] = None,
                ) extends Local

case object Stop extends Local

case object Nuke extends Local

case object StartNomad extends Local

sealed trait DNS extends Runtime

case class DNSUp(service: Option[String], bindIP: Option[String]) extends DNS

case class DNSDown(service: Option[String], bindIP: Option[String]) extends DNS

case class FlagCmd(
                    flags: List[String],
                    enable: Boolean,
                    remoteAddress: Option[String],
                    accessToken: Option[String]
                  ) extends Runtime

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

  val runtime = subparser[Runtime](
    command(
      "runtime",
      info(
        subparser[Runtime](
          command(
            "start",
            info(
              switch(long("dry-run"), help("whether to run the mock interpreter")).map({ flag =>
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
              }) <*> optional(
                strOption(long("remote-address"),
                  help("remote consul address")))
                .map(ra => { exist: Start =>
                  exist.copy(remoteAddress = ra)
                }) <*> optional(
                intOption(long("service-port"),
                  help("Kafka service port")))
                .map(sp => { exist: Start =>
                  sp match {
                    case str@Some(_) => exist.copy(servicePort = str.get)
                    case None => exist
                  }
                }) <*> optional(
                strOption(long("token"),
                  help("ACL access token")))
                .map(token => { exist: Start =>
                  exist.copy(accessToken = token)
                }) <*> optional(
                intOption(long("registry-listener-port"),
                  help("Kafka registry listener port")))
                .map(rlp => { exist: Start =>
                  rlp match {
                    case str@Some(_) => exist.copy(registryListenerPort = rlp.get)
                    case None => exist
                  }
                }) <*> optional(
                strOption(long("prefix"),
                  help("Nomad job prefix")))
                .map(prefix => { exist: Start =>
                  prefix match {
                    case Some(_) => exist.copy(prefix = prefix)
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
          ),
          command("enable", info(^^(
            many(strArgument(metavar("FLAGS"))),
            optional(strOption(long("remote-address"), help("remote consul address"))),
            optional(strOption(long("token"), help("ACL access token")))
          )(FlagCmd(_, true, _, _)), progDesc("enable feature flags"))),
          command("disable", info(^^(
            many(strArgument(metavar("FLAGS"))),
            optional(strOption(long("remote-address"), help("remote consul address"))),
            optional(strOption(long("token"), help("ACL access token")))
          )(FlagCmd(_, false, _, _)), progDesc("disable feature flags")))
        ),
        progDesc("radix runtime component")
      )
    )
  ) <*> helper


  val oauth = subparser[Oauth](
    command("oauth", info(subparser[Oauth](command("google-sheets", info(pure(GoogleSheets),
      progDesc("set up a google sheets token")))))
    ))

  val res: Parser[RadixCMD] = runtime.weaken[RadixCMD]

  val opts =
    info(res <*> helper,
      progDesc("Welcome to Timberland"),
      header(""))

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
    val vault = new File(persistentdir + "/vault/vault")
    val nginx = new File(persistentdir + "/nginx/")
    nginx.mkdirs
    val minio = new File("/opt/radix/minio_data/")
    minio.mkdirs
    val minio_bucket = new File("/opt/radix/minio_data/userdata")
    minio_bucket.mkdirs
    nomad.getParentFile.mkdirs()
    consul.getParentFile.mkdirs()
    vault.getParentFile.mkdirs()

    def cmdEval(cmd: RadixCMD): Unit = {
      cmd match {
        case run: Runtime =>
          run match {
            case local: Local =>
              local match {
                case Nuke => Right(Unit)
                case cmd@Start(dummy, loglevel, bindIP, consulSeedsO, remoteAddress, servicePort, accessToken, registryListenerPort, prefix) => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(minimumLevel = Some(loglevel))
                    .replace()
                  scribe.info(s"starting runtime with $cmd")

                  import ammonite.ops._
                  System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))

                  if (dummy) {
                    implicit val host = new Mock.RuntimeNolaunch[IO]
                    Right(
                      println(Run
                        .initializeRuntimeProg[IO](Path(consul.toPath.getParent), Path(nomad.toPath.getParent), bindIP, consulSeedsO, 0)
                        .unsafeRunSync)
                    )
                    System.exit(0)
                  } else {
                    scribe.info("Creating weave network")
                    val createWeaveNetwork = for {
                      _ <- IO(os.proc("/usr/bin/sudo /sbin/sysctl -w vm.max_map_count=262144".split(' ')).spawn())
                      pluginList <- IO(os.proc("/usr/bin/docker plugin ls".split(' ')).call(cwd = os.root, check = false))
                      _ <- pluginList.out.string.contains("weaveworks/net-plugin:2.6.0") match {
                        case true => {
                          IO(os.proc("/usr/bin/docker network create --driver=weaveworks/net-plugin:2.6.0 --attachable weave  --ip-range 10.32.0.0/12 --subnet 10.32.0.0/12".split(' ')).call(cwd = os.root, stdout = os.Inherit, check = false))
                          IO.pure(scribe.info("Weave network exists or was created"))
                        }
                        case false => IO.pure(scribe.info("Weave plugin not installed. Skipping creation of weave network."))
                      }

                    } yield ()

                    implicit val host = new Run.RuntimeServicesExec[IO]
                    val startServices = for {
                      localFlags <- flags.flags.getLocalFlags(Path(persistentdir))

                      consulPath = Path(consul.toPath.getParent)
                      nomadPath = Path(nomad.toPath.getParent)
                      bootstrapExpect = if (localFlags.getOrElse("dev", true)) 1 else 3

                      consulUp <- daemonutil.isPortUp(8500)
                      masterToken <- (consulUp, accessToken) match {
                        // If consul is up and token is specified, skip the service setup
                        case (true, Some(token)) => IO.pure(token)
                        // If consul is down and no token is specified, do the service setup
                        case (false, None) =>
                          IO(scribe.info(s"***********DAEMON SIZE${bootstrapExpect}***************")) *>
                            Run.initializeRuntimeProg[IO](consulPath, nomadPath, bindIP, consulSeedsO, bootstrapExpect)
                        case (true, None) =>
                          scribe.error("Please provide an access token to connect to the existing nomad/consul instances")
                          sys.exit(1)
                        case (false, Some(token)) =>
                          scribe.error("System has not been bootstrapped, so an access token cannot be specified")
                          sys.exit(1)
                      }
                    } yield masterToken

                    scribe.info("Launching daemons")
                    val bootstrap = for {
                      _ <- createWeaveNetwork
                      masterToken <- startServices
                      _ <- acl.storeMasterTokenInVault(Path(persistentdir), masterToken)

                      flagUpdateResp <- flags.flags.updateFlags(Path(persistentdir), Some(masterToken))
                      featureFlags = flagUpdateResp.asInstanceOf[ConsulFlagsUpdated].flags

                      _ <- daemonutil.runTerraform(featureFlags, masterToken, integrationTest = false, prefix)
                      _ <- daemonutil.waitForQuorum(featureFlags)
                    } yield ()

                    val remoteBootstrap = for {
                      serviceAddrs <- daemonutil.getServiceIps(remoteAddress.getOrElse("127.0.0.1"))
                      masterToken = accessToken.get
                      featureFlags <- flags.flags.getConsulFlags(masterToken)(serviceAddrs)

                      _ <- (new VaultStarter).initializeAndUnsealAndSetupVault()
                      _ <- daemonutil.runTerraform(featureFlags, masterToken, integrationTest = false, prefix)(serviceAddrs)
                    } yield ()

                    (remoteAddress, accessToken) match {
                      case (Some(_), Some(_)) => remoteBootstrap.unsafeRunSync()
                      case (None, _) => bootstrap.unsafeRunSync()
                      case (Some(_), None) =>
                        scribe.error("Access token required for remote instances")
                        sys.exit(1)
                    }
                    sys.exit(0)
                  }
                }
                case Stop => {
                  implicit val host = new Run.RuntimeServicesExec[IO]
                  (daemonutil.stopTerraform(integrationTest = false) *>
                    Run.stopRuntimeProg[IO]() *>
                    IO(scribe.info("Stopped."))).unsafeRunSync
                  sys.exit(0)
                }
                case StartNomad => Right(Unit)
              }
            case dns: DNS => {
              scribe.Logger.root
                .clearHandlers()
                .clearModifiers()
                .withHandler(minimumLevel = Some(scribe.Level.Debug))
                .replace()
              val dns_set = dns match {
                case DNSUp(service, bindIP) => launch.dns.up()
                case DNSDown(service, bindIP) => launch.dns.down()
              }
              dns_set.unsafeRunSync()
            }
            case FlagCmd(flagNames, enable, remoteAddress, accessToken) => {
              System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
              val flagMap = flagNames.map((_, enable)).toMap

              val localProc = for {
                flagUpdateResp <- flags.flags.updateFlags(Path(persistentdir), accessToken, flagMap)
                _ <- flagUpdateResp match {
                  case ConsulFlagsUpdated(featureFlags) =>
                    daemonutil.runTerraform(featureFlags, accessToken.get, integrationTest = false, None) *>
                    daemonutil.waitForQuorum(featureFlags)
                  case FlagsStoredLocally() =>
                    flagConfig.updateFlagConfig(flagMap, None)(ServiceAddrs(), Path(persistentdir))
                }
              } yield ()

              val remoteProc = for {
                serviceAddrs <- daemonutil.getServiceIps(remoteAddress.getOrElse(""))
                flagUpdateResp <- flags.flags.updateFlags(Path(persistentdir), accessToken, flagMap)(serviceAddrs)
                _ <- flagUpdateResp match {
                  case ConsulFlagsUpdated(featureFlags) =>
                    daemonutil.runTerraform(featureFlags, accessToken.get, integrationTest = false, None)(serviceAddrs)
                  case FlagsStoredLocally() =>
                    flagConfig.updateFlagConfig(flagMap, None)(serviceAddrs, Path(persistentdir)) *>
                    IO(scribe.warn("Could not connect to remote consul instance. Flags stored locally."))
                }
              } yield ()

              (remoteAddress, accessToken) match {
                case (Some(_), Some(_)) => remoteProc.unsafeRunSync()
                case (None, Some(_)) => localProc.unsafeRunSync()
                case (None, None) =>
                  scribe.warn("No access token specified. Writing to local flag file")
                  localProc.unsafeRunSync()
                case (Some(_), None) =>
                  scribe.error("Please provide a valid access token for the remote instances")
                  sys.exit(1)
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
