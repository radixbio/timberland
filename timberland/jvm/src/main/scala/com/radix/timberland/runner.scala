package com.radix.timberland

import java.io.File

import ammonite.ops._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.radix.timberland.flags.{RemoteConfig, _}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.{LogTUI, LogTUIWriter, VaultStarter, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.timberland.util.{UpdateModules, VaultStarter}
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.Parser
import org.http4s.implicits._
import org.http4s.client.blaze.BlazeClientBuilder
import scalaz.syntax.apply._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.AnsiColor
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
  prefix: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None
) extends Local

case object Stop extends Local

case object Nuke extends Local

case object StartNomad extends Local

case class Update(
  remoteAddress: Option[String] = None,
  prefix: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None
) extends Local

sealed trait DNS extends Runtime

case class DNSUp(service: Option[String], bindIP: Option[String]) extends DNS

case class DNSDown(service: Option[String], bindIP: Option[String]) extends DNS

case class FlagSet(
  flags: List[String],
  enable: Boolean,
  remoteAddress: Option[String],
  username: Option[String],
  password: Option[String]
) extends Runtime

case class FlagQuery(remoteAddress: Option[String], username: Option[String], password: Option[String]) extends Runtime

case class AddUser(name: String, roles: List[String]) extends Runtime

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
                      case Some("info")  => exist.copy(loglevel = scribe.Level.Info)
                      case Some("trace") => exist.copy(loglevel = scribe.Level.Trace)
                      case Some(flag)    => throw new IllegalArgumentException(s"$flag isn't a valid loglevel")
                      case None          => exist
                    }
                }) <*> optional(
                strOption(
                  long("force-bind-ip"),
                  help("force services to use the specified subnet and IP (in form \"192.168.1.5\")")
                )
              ).map(subnet => {
                exist: Start =>
                  subnet match {
                    case str @ Some(_) => exist.copy(bindIP = str)
                    case None          => exist
                  }
              }) <*> optional(
                strOption(
                  long("consul-seeds"),
                  help("comma separated list of seed nodes for consul (maps to retry_join in consul.json)")
                )
              ).map(seeds => {
                exist: Start =>
                  seeds match {
                    case list @ Some(_) => exist.copy(consulSeeds = list)
                    case None           => exist
                  }
              }) <*> optional(strOption(long("remote-address"), help("remote consul address")))
                .map(ra => { exist: Start => exist.copy(remoteAddress = ra) }) <*> optional(
                strOption(long("prefix"), help("Nomad job prefix"))
              ).map(prefix => {
                exist: Start =>
                  prefix match {
                    case Some(_) => exist.copy(prefix = prefix)
                    case None    => exist
                  }
              }) <*> optional(strOption(long("username"), help("timberland username")))
                .map(username => {
                  exist: Start =>
                    username match {
                      case Some(_) => exist.copy(username = username)
                      case None    => exist
                    }
                }) <*> optional(strOption(long("password"), help("timberland password")))
                .map(password => {
                  exist: Start =>
                    password match {
                      case Some(_) => exist.copy(password = password)
                      case None    => exist
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
            info(
              subparser[DNS](
                command(
                  "up",
                  info(
                    ^(optional(strOption(long("service"))), optional(strOption(long("force-bind-ip"))))(DNSUp),
                    progDesc("inject consul into dns resolution")
                  )
                ),
                command(
                  "down",
                  info(
                    ^(optional(strOption(long("service"))), optional(strOption(long("force-bind-ip"))))(DNSDown),
                    progDesc("deinject consul from dns resolution")
                  )
                )
              ).weaken[Runtime]
            )
          ),
          command(
            "add_user",
            info(
              ^(
                strArgument(metavar("NAME")),
                many(strArgument(metavar("[POLICIES...]")))
              )(AddUser)
            )
          ),
          command(
            "enable",
            info(
              ^^^(
                many(strArgument(metavar("FLAGS"))),
                optional(strOption(long("remote-address"), help("remote consul address"))),
                optional(strOption(long("username"), help("timberland username"))),
                optional(strOption(long("password"), help("timberland password")))
              )(FlagSet(_, true, _, _, _)),
              progDesc("enable feature flags")
            )
          ),
          command(
            "disable",
            info(
              ^^^(
                many(strArgument(metavar("FLAGS"))),
                optional(strOption(long("remote-address"), help("remote consul address"))),
                optional(strOption(long("username"), help("timberland username"))),
                optional(strOption(long("password"), help("timberland password")))
              )(FlagSet(_, false, _, _, _)),
              progDesc("disable feature flags")
            )
          ),
          command(
            "query",
            info(
              ^^(
                optional(strOption(long("remote-address"), help("remote consul address"))),
                optional(strOption(long("username"), help("timberland username"))),
                optional(strOption(long("password"), help("timberland password")))
              )(FlagQuery),
              progDesc("query current state of feature flags")
            )
          ),
          command(
            "update",
            info(
              pure(Update()) <*> optional(strOption(long("remote-address"), help("remote consul address")))
                .map(ra => { exist: Update => exist.copy(remoteAddress = ra) }) <*> optional(
                strOption(long("prefix"), help("Nomad job prefix"))
              ).map(prefix => {
                exist: Update =>
                  prefix match {
                    case Some(_) => exist.copy(prefix = prefix)
                    case None    => exist.copy(prefix = Some(daemonutil.getPrefix(false)))
                  }
              }) <*> optional(strOption(long("username"), help("timberland username")))
                .map(username => {
                  exist: Update =>
                    username match {
                      case Some(_) => exist.copy(username = username)
                      case None    => exist
                    }
                }) <*> optional(strOption(long("password"), help("timberland password")))
                .map(password => {
                  exist: Update =>
                    password match {
                      case Some(_) => exist.copy(password = password)
                      case None    => exist
                    }
                })
            )
          )
        ),
        progDesc("radix runtime component")
      )
    )
  ) <*> helper

  val oauth = subparser[Oauth](
    command(
      "oauth",
      info(
        subparser[Oauth](command("google-sheets", info(pure(GoogleSheets), progDesc("set up a google sheets token"))))
      )
    )
  )

  val res: Parser[RadixCMD] = runtime.weaken[RadixCMD] <|> oauth.weaken[RadixCMD]

  val opts =
    info(res <*> helper, progDesc("Welcome to Timberland"), header(""))

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
    val osname = System.getProperty("os.name") match {
      case mac if mac.toLowerCase.contains("mac")       => "darwin"
      case linux if linux.toLowerCase.contains("linux") => "linux"
    }
    val arch = System.getProperty("os.arch") match {
      case x86 if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains("x86") => "amd64"
      case _                                                                           => "arm"
    }

    val persistentDirStr = "/opt/radix/timberland/" // TODO make configurable
    implicit val persistentDirPath = Path(persistentDirStr)

    val systemdDir = "/opt/radix/systemd/"
    val appdatadir = new File(persistentDirStr)
    val consul = new File(persistentDirStr + "/consul/consul")
    val nomad = new File(persistentDirStr + "/nomad/nomad")
    val vault = new File(persistentDirStr + "/vault/vault")
    val nginx = new File(persistentDirStr + "/nginx/")
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
                case cmd @ Start(dummy, loglevel, bindIP, consulSeedsO, remoteAddress, prefix, username, password) => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(scribe.handler.SynchronousLogHandler(writer = LogTUIWriter()))
                    .replace()
                  scribe.info(s"starting runtime with $cmd")

                  import ammonite.ops._
                  System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))

                  if (dummy) {
                    implicit val host = new Mock.RuntimeNolaunch[IO]
                    Right(
                      println(
                        Run
                          .initializeRuntimeProg[IO](
                            Path(consul.toPath.getParent),
                            Path(nomad.toPath.getParent),
                            bindIP,
                            consulSeedsO,
                            0
                          )
                          .unsafeRunSync
                      )
                    )
                    System.exit(0)
                  } else {
                    val createWeaveNetwork = for {
                      _ <- IO(scribe.info("Creating weave network"))
                      _ <- IO(os.proc("/usr/bin/sudo /sbin/sysctl -w vm.max_map_count=262144".split(' ')).spawn())
                      pluginList <- IO(
                        os.proc("/usr/bin/docker plugin ls".split(' ')).call(cwd = os.root, check = false)
                      )
                      _ <- pluginList.out.string.contains("weaveworks/net-plugin:2.6.0") match {
                        case true => {
                          IO(
                            os.proc(
                                "/usr/bin/docker network create --driver=weaveworks/net-plugin:2.6.0 --attachable weave  --ip-range 10.32.0.0/12 --subnet 10.32.0.0/12"
                                  .split(' ')
                              )
                              .call(cwd = os.root, stdout = os.Inherit, check = false)
                          )
                          IO.pure(scribe.info("Weave network exists or was created"))
                        }
                        case false =>
                          IO.pure(scribe.info("Weave plugin not installed. Skipping creation of weave network."))
                      }
                    } yield ()

                    implicit val host = new Run.RuntimeServicesExec[IO]
                    val startServices = for {
                      _ <- IO(scribe.info("Launching daemons"))
                      localFlags <- featureFlags.getLocalFlags(persistentDirPath)
                      // Starts LogTUI before `startLogTuiAndRunTerraform` is called
                      _ <- if (localFlags.getOrElse("tui", true)) LogTUI.startTUI() else IO.unit
                      consulPath = Path(consul.toPath.getParent)
                      nomadPath = Path(nomad.toPath.getParent)
                      bootstrapExpect = if (localFlags.getOrElse("dev", true)) 1 else 3
                      tokens <- Run
                        .initializeRuntimeProg[IO](consulPath, nomadPath, bindIP, consulSeedsO, bootstrapExpect)
                      _ <- daemonutil.waitForDNS("vault.service.consul", 10.seconds)
                    } yield tokens

                    def startLogTuiAndRunTerraform(
                      featureFlags: Map[String, Boolean],
                      serviceAddrs: ServiceAddrs,
                      authTokens: AuthTokens,
                      waitForQuorum: Boolean
                    ) =
                      for {
                        _ <- if (featureFlags.getOrElse("tui", true)) LogTUI.startTUI() else IO.unit
                        _ <- IO(LogTUI.writeLog(s"using flags: $featureFlags"))
                        tfStatus <- daemonutil.runTerraform(featureFlags, prefix = prefix)(serviceAddrs, authTokens)
                        _ <- IO {
                          tfStatus match {
                            case 0 => true
                            case code => {
                              LogTUI.writeLog("runTerraform failed, exiting")
                            }
                            sys.exit(code)
                          }
                        }
                        _ <- if (waitForQuorum) daemonutil.waitForQuorum(featureFlags) else IO.unit
                      } yield ()

                    // Called when consul/nomad/vault are on localhost
                    val localBootstrap = for {
                      hasBootstrapped <- IO(os.exists(persistentDirPath / ".bootstrap-complete"))
                      serviceAddrs = ServiceAddrs()
                      authTokens <- if (hasBootstrapped) {
                        auth.getAuthTokens(isRemote = false, serviceAddrs, username, password)
                      } else {
                        // daemonutil.containerRegistryLogin(containerRegistryUser, containerRegistryToken) *>
                        flagConfig.promptForDefaultConfigs *> createWeaveNetwork *> startServices
                      }
                      featureFlags <- featureFlags.updateFlags(persistentDirPath, Some(authTokens), confirm = true)(
                        serviceAddrs
                      )
                      _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = true)
                    } yield ()

                    // Called when consul/vault/nomad are not on localhost
                    val remoteBootstrap = for {
                      serviceAddrs <- daemonutil.getServiceIps()
                      authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                      featureFlags <- featureFlags.updateFlags(persistentDirPath, Some(authTokens))(serviceAddrs)
                      _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = false)
                    } yield ()

                    val bootstrapIO = if (remoteAddress.isDefined) remoteBootstrap else localBootstrap
                    val bootstrap = bootstrapIO.handleErrorWith(err => LogTUI.endTUI(Some(err))) *> LogTUI.endTUI()

                    bootstrap.unsafeRunSync()
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
                case cmd @ Update(remoteAddress, prefix, username, password) => {

                  System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))

                  val consulExistsProc = for {
                    serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
                    authTokens <- auth.getAuthTokens(
                      isRemote = remoteAddress.isDefined,
                      serviceAddrs,
                      username,
                      password
                    )
                    flagMap <- featureFlags.updateFlags(persistentDirPath, Some(authTokens))(serviceAddrs)
                    _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
                    _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
                  } yield true

                  daemonutil
                    .isPortUp(8500)
                    .flatMap {
                      case true  => UpdateModules.run(consulExistsProc, prefix = prefix)
                      case false => IO.unit
                    }
                    .unsafeRunSync()
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
                case DNSUp(service, bindIP)   => launch.dns.up()
                case DNSDown(service, bindIP) => launch.dns.down()
              }
              dns_set.unsafeRunSync()
            }

            case FlagSet(flagNames, enable, remoteAddress, username, password) =>
              System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
              val flagsToSet = flagNames.map((_, enable)).toMap

              val consulExistsProc = for {
                serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
                authTokens <- auth.getAuthTokens(isRemote = remoteAddress.isDefined, serviceAddrs, username, password)
                flagMap <- featureFlags.updateFlags(
                  persistentDirPath,
                  Some(authTokens),
                  flagsToSet,
                  confirm = remoteAddress.isDefined
                )(serviceAddrs)
                _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
                _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
              } yield ()

              val noConsulProc = for {
                flagMap <- featureFlags.updateFlags(persistentDirPath, None, flagsToSet)
                _ <- flagConfig.updateFlagConfig(flagMap)
                _ <- IO(scribe.warn("Could not connect to remote consul instance. Flags stored locally."))
              } yield ()

              IO(os.exists(persistentDirPath / ".bootstrap-complete"))
                .flatMap {
                  case true  => consulExistsProc
                  case false => noConsulProc
                }
                .unsafeRunSync()

            case FlagQuery(remoteAddress, username, password) =>
              System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
              val io = for {
                serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
                consulIsUp <- featureFlags.isConsulUp()(serviceAddrs)
                authTokens <- if (consulIsUp) {
                  auth.getAuthTokens(isRemote = remoteAddress.isDefined, serviceAddrs, username, password).map(Some(_))
                } else IO.pure(None)
                _ <- featureFlags.printFlagInfo(persistentDirPath, consulIsUp, serviceAddrs, authTokens)
              } yield ()
              io.unsafeRunSync()
              sys.exit(0)

            case AddUser(name, policies) =>
              if (!os.exists(persistentDirPath / ".bootstrap-complete")) {
                Console.err.println("Please run timberland runtime start before adding a new user")
                sys.exit(1)
              }
              val policiesWithDefault = if (policies.nonEmpty) policies else List("remote-access")
              val vaultToken = (new VaultUtils).findVaultToken()
              implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
              BlazeClientBuilder[IO](global).resource
                .use { client =>
                  val vault = new Vault[IO](Some(vaultToken), uri"http://127.0.0.1:8200", client)
                  for {
                    password <- IO(System.console.readPassword("Password> ").mkString)
                    _ <- vault.enableAuthMethod("userpass")
                    res <- vault.createUser(name, password, policiesWithDefault)
                  } yield res match {
                    case Right(_) => ()
                    case Left(err) =>
                      println(err)
                      sys.exit(1)
                  }
                }
                .unsafeRunSync()
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
