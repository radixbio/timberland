package com.radix.timberland

import java.io.File

import ammonite.ops._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.radix.timberland.flags.{RemoteConfig, _}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.{LogTUI, LogTUIWriter, RadPath, UpdateModules, Util, VaultStarter, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
import io.circe.{Parser => _}
import optparse_applicative._
import org.http4s.implicits._
import com.radix.utils.tls.ConsulVaultSSLContext._
import jdk.jshell.spi.ExecutionControl.NotImplementedException
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
      case mac if mac.toLowerCase.contains("mac")             => "darwin"
      case linux if linux.toLowerCase.contains("linux")       => "linux"
      case windows if windows.toLowerCase.contains("windows") => "windows"
    }
    val arch = System.getProperty("os.arch") match {
      case x86 if x86.toLowerCase.contains("amd64") || x86.toLowerCase.contains("x86") => "amd64"
      case _                                                                           => "arm"
    }

    val persistentDir = RadPath.runtime / "timberland"

    val consul = persistentDir / "consul" / "consul"
    val nomad = persistentDir / "nomad" / "nomad"
    val vault = persistentDir / "vault" / "vault"
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
                case cmd @ Start(
                      loglevel,
                      bindIP,
                      leaderNode,
                      remoteAddress,
                      prefix,
                      username,
                      password,
                      serverJoin
                    ) => {
                  scribe.Logger.root
                    .clearHandlers()
                    .clearModifiers()
                    .withHandler(scribe.handler.SynchronousLogHandler(writer = LogTUIWriter()))
                    .replace()
                  scribe.info(s"starting runtime with $cmd")

                  import ammonite.ops._
                  System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))

                  val createWeaveNetwork = if (osname != "windows") for {
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
                  else IO.unit

                  def startServices(setupACL: Boolean) =
                    for {
                      _ <- IO(scribe.info("Launching daemons"))
                      localFlags <- featureFlags.getLocalFlags(persistentDir)
                      // Starts LogTUI before `startLogTuiAndRunTerraform` is called
                      // only logtui on linux amd64
                      _ <- if (localFlags.getOrElse("tui", true) & System.getProperty("os.arch") == "amd64" & System
                                 .getProperty("os.name")
                                 .toLowerCase
                                 .contains("linux"))
                        LogTUI.startTUI()
                      else IO.unit
                      consulPath = consul / os.up
                      nomadPath = nomad / os.up
                      bootstrapExpect = if (localFlags.getOrElse("dev", true)) 1 else 3
                      tokens <- Services.startServices(
                        consulPath,
                        nomadPath,
                        bindIP,
                        leaderNode,
                        bootstrapExpect,
                        setupACL,
                        serverJoin
                      )
                      _ <- Util.waitForDNS("vault.service.consul", 30.seconds)
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
                    _ <- IO(
                      if (!os.exists(persistentDir / "consul" / "config"))
                        os.makeDir(persistentDir / "consul" / "config")
                      else ()
                    )
                    hasBootstrapped <- IO(os.exists(persistentDir / ".bootstrap-complete"))
                    isConsulUp <- Util.isPortUp(8501)
                    defaultServiceAddrs = ServiceAddrs()
                    clientJoin = (leaderNode.isDefined && !serverJoin)
                    remoteJoin = clientJoin | serverJoin
                    windowsCheck = if (osname == "windows" & !remoteJoin)
                      throw new UnsupportedOperationException(
                        "Windows only supports joining, you must pass a leader node."
                      )
                    else ()
                    authTokens <- (hasBootstrapped, isConsulUp, remoteJoin) match {
                      case (true, true, false) =>
                        auth.getAuthTokens(isRemote = false, defaultServiceAddrs, username, password)
                      case (true, false, false) =>
                        startServices(setupACL = false)
                      case (false, _, false) =>
                        flagConfig.promptForDefaultConfigs(persistentDir = persistentDir) *>
                          createWeaveNetwork *> startServices(setupACL = true)
                      case (false, _, true) =>
                        for {
                          // TODO: double check ServiceAddrs = otherConsul is fair
                          serviceAddrs <- IO(leaderNode match {
                            case Some(otherConsul) => ServiceAddrs(otherConsul, otherConsul, otherConsul)
                            case None              => defaultServiceAddrs
                          })
                          authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                          storeIntermediateToken <- auth.storeIntermediateToken(authTokens.consulNomadToken)
                          storeTokens <- auth.writeTokenConfigs(persistentDir, authTokens.consulNomadToken)
                          storeVaultToken <- IO((new VaultUtils).storeVaultTokenKey("", authTokens.vaultToken))
                          stillAuthTokens <- startServices(setupACL = true)
                        } yield authTokens
                      case (true, true, true) =>
                        for {
                          // TODO: double check ServiceAddrs = otherConsul is fair
                          serviceAddrs <- IO(leaderNode match {
                            case Some(otherConsul) => ServiceAddrs(otherConsul, otherConsul, otherConsul)
                            case None              => defaultServiceAddrs
                          })
                          authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                          storeTokens <- auth.writeTokenConfigs(persistentDir, authTokens.consulNomadToken)
                          storeVaultToken <- IO((new VaultUtils).storeVaultTokenKey("", authTokens.vaultToken))
                        } yield authTokens
                    }
                    // flags already written to consul by leader (first node)
                    featureFlags <- if (!remoteJoin) for {
                      featureFlags <- featureFlags.updateFlags(persistentDir, Some(authTokens), confirm = true)(
                        defaultServiceAddrs
                      )
                      _ <- startLogTuiAndRunTerraform(
                        featureFlags,
                        defaultServiceAddrs,
                        authTokens,
                        waitForQuorum = true
                      )
                    } yield featureFlags
                    else IO.unit
                  } yield ()

                  // Called when consul/vault/nomad are not on localhost
                  val remoteBootstrap = for {
                    serviceAddrs <- daemonutil.getServiceIps()
                    authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                    featureFlags <- featureFlags.updateFlags(persistentDir, Some(authTokens))(serviceAddrs)
                    _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = false)
                  } yield ()
                  val bootstrapIO = if (remoteAddress.isDefined) remoteBootstrap else localBootstrap
                  val bootstrap = bootstrapIO.handleErrorWith(err => LogTUI.endTUI(Some(err)) *> IO(throw err)) *> LogTUI
                    .endTUI()

                  bootstrap.unsafeRunSync()
                  sys.exit(0)
                }
                case Stop => {
                  (daemonutil.stopTerraform(integrationTest = false) *>
                    Services.stopServices() *>
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

                  Util
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
              sys.exit(0)
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
                  scribe.error(s"$err")
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
