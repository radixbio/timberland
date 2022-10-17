package com.radix.timberland

import java.io.File

import ammonite.ops._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.radix.timberland.flags.{RemoteConfig, _}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.{LogTUI, LogTUIWriter, VaultStarter, VaultUtils, RadPath, UpdateModules}
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.timberland.util.{UpdateModules, VaultStarter}
import io.circe.{Parser => _}
import optparse_applicative._
import org.http4s.implicits._
import com.radix.utils.tls.ConsulVaultSSLContext._
import scalaz.syntax.apply._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.StdIn.readLine

object runner {

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


    val persistentDir = RadPath.runtime / "timberland"

    val consul = persistentDir / "consul" / "consul"
    val nomad = persistentDir / "nomad" / "nomad"
    val vault = persistentDir / "vault"/ "vault"
    val nginx = persistentDir / "nginx"
    os.makeDir.all(nginx)
    val minio = persistentDir / "minio_data"
    os.makeDir.all(minio)
    val minio_bucket = minio / "userdata"
    os.makeDir.all(minio_bucket)
    os.makeDir.all(nomad / os.up)
    os.makeDir.all(consul / os.up)
    os.makeDir.all(vault / os.up)

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
                      println(Run
                        .initializeRuntimeProg[IO](consul / os.up, nomad / os.up, bindIP, consulSeedsO, 0, false)
                        .unsafeRunSync)
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
                    def startServices(setupACL: Boolean) = for {
                      _ <- IO(scribe.info("Launching daemons"))
                      localFlags <- featureFlags.getLocalFlags(persistentDir)
                      // Starts LogTUI before `startLogTuiAndRunTerraform` is called
                      _ <- if (localFlags.getOrElse("tui", true)) LogTUI.startTUI() else IO.unit
                      consulPath = consul / os.up
                      nomadPath = nomad / os.up
                      bootstrapExpect = if (localFlags.getOrElse("dev", true)) 1 else 3
                      tokens <- Run.initializeRuntimeProg[IO](
                        consulPath, nomadPath, bindIP, consulSeedsO, bootstrapExpect, setupACL
                      )
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
                      hasBootstrapped <- IO(os.exists(persistentDir / ".bootstrap-complete"))
                      isConsulUp <- daemonutil.isPortUp(8501)
                      serviceAddrs = ServiceAddrs()
                      authTokens <- (hasBootstrapped, isConsulUp) match {
                        case (true, true) =>
                          auth.getAuthTokens(isRemote = false, serviceAddrs, username, password)
                        case (true, false) =>
                          startServices(setupACL = false)
                        case (false, _) =>
                          flagConfig.promptForDefaultConfigs(persistentDir = persistentDir) *>
                            createWeaveNetwork *> startServices(setupACL = true)
                      }
                      featureFlags <- featureFlags.updateFlags(persistentDir, Some(authTokens), confirm = true)(serviceAddrs)
                      _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = true)
                    } yield ()

                    // Called when consul/vault/nomad are not on localhost
                    val remoteBootstrap = for {
                      serviceAddrs <- daemonutil.getServiceIps()
                      authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                      featureFlags <- featureFlags.updateFlags(persistentDir, Some(authTokens))(serviceAddrs)
                      _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = false)
                    } yield ()

                    val bootstrapIO = if (remoteAddress.isDefined) remoteBootstrap else localBootstrap
                    val bootstrap = bootstrapIO.handleErrorWith(err => LogTUI.endTUI(Some(err)) *> IO(throw err)) *> LogTUI.endTUI()

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
                    flagMap <- featureFlags.updateFlags(persistentDir, Some(authTokens))(serviceAddrs)
                    _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
                    _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
                  } yield true

                  daemonutil
                    .isPortUp(8501)
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
                case DNSUp   => launch.dns.up()
                case DNSDown => launch.dns.down()
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
                  persistentDir,
                  Some(authTokens),
                  flagsToSet,
                  confirm = remoteAddress.isDefined
                )(serviceAddrs)
                _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
                _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
              } yield ()

              val noConsulProc = for {
                flagMap <- featureFlags.updateFlags(persistentDir, None, flagsToSet)
                _ <- flagConfig.updateFlagConfig(flagMap)(persistentDir = persistentDir)
                _ <- IO(scribe.warn("Could not connect to remote consul instance. Flags stored locally."))
              } yield ()

              IO(os.exists(persistentDir / ".bootstrap-complete"))
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
                _ <- featureFlags.printFlagInfo(persistentDir, consulIsUp, serviceAddrs, authTokens)
              } yield ()
              io.unsafeRunSync()
              sys.exit(0)

            case AddUser(name, policies) =>
              if (!os.exists(persistentDir / ".bootstrap-complete")) {
                Console.err.println("Please run timberland runtime start before adding a new user")
                sys.exit(1)
              }
              val policiesWithDefault = if (policies.nonEmpty) policies else List("remote-access")
              val vaultToken = (new VaultUtils).findVaultToken()
              implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
              val vault = new Vault[IO](Some(vaultToken), uri"https://127.0.0.1:8200")
              val proc = for {
                password <- IO(System.console.readPassword("Password> ").mkString)
                _ <- vault.enableAuthMethod("userpass")
                res <- vault.createUser(name, password, policiesWithDefault)
              } yield res match {
                case Right(_) => ()
                case Left(err) =>
                  println(err)
                  sys.exit(1)
              }
              proc.unsafeRunSync()
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
      cmdEval(execParser(args, "timberland", cli.opts))
    } catch {
      case os.SubprocessException(result) => sys.exit(result.exitCode)
    }
  }

}
