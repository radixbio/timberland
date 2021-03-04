package com.radix.timberland

import java.io.File

import ammonite.ops._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.radix.timberland.flags.hooks.ensureSupported
import com.radix.timberland.flags.{RemoteConfig, _}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.{LogTUI, LogTUIWriter, OAuthController, RadPath, UpdateModules, Util, VaultStarter, VaultUtils}
import com.radix.utils.helm.http4s.vault.Vault
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

    def cmdEval(cmd: RadixCMD): Unit = {
      cmd match {
        case cmd @ Start(
              loglevel,
              bindIP,
              leaderNode,
              remoteAddress,
              prefix,
              username,
              password,
              serverJoin
            ) =>
          scribe.Logger.root
            .clearHandlers()
            .clearModifiers()
            .withHandler(scribe.handler.SynchronousLogHandler(writer = LogTUIWriter()))
            .replace()
          scribe.info(s"starting runtime with $cmd")

          import ammonite.ops._
          System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
          // disable DNS cache
          System.setProperty("networkaddress.cache.ttl", "0")

          def startServices(setupACL: Boolean) =
            for {
              _ <- IO(scribe.info("Launching daemons"))
              localFlags <- featureFlags.getLocalFlags(RadPath.persistentDir)
              // Starts LogTUI before `startLogTuiAndRunTerraform` is called
              _ <- if (localFlags.getOrElse("tui", true)) LogTUI.startTUI() else IO.unit
              bootstrapExpect = if (localFlags.getOrElse("dev", true)) 1 else 3
              tokens <- Services.startServices(bindIP, leaderNode, bootstrapExpect, setupACL, serverJoin)
              _ <- Util.waitForDNS("vault.service.consul", 30.seconds)
            } yield tokens

          // only run on leader node, or with a remote address as a target
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
              if (!os.exists(RadPath.consulExec / os.up / "config"))
                os.makeDir(RadPath.consulExec / os.up / "config")
              else ()
            )
            hasBootstrapped <- IO(os.exists(RadPath.persistentDir / ".bootstrap-complete"))
            isConsulUp <- Util.isPortUp(8501)
            defaultServiceAddrs = ServiceAddrs()
            clientJoin = (leaderNode.isDefined && !serverJoin)
            remoteJoin = clientJoin | serverJoin
            windowsCheck = if (ensureSupported.osname == "windows" & !remoteJoin)
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
                flagConfig.promptForDefaultConfigs(persistentDir = RadPath.persistentDir) *>
                  startServices(setupACL = true)
              case (false, _, true) =>
                for {
                  // TODO: double check ServiceAddrs = otherConsul is fair
                  serviceAddrs <- IO(leaderNode match {
                    case Some(otherConsul) => ServiceAddrs(otherConsul, otherConsul, otherConsul)
                    case None              => defaultServiceAddrs
                  })
                  authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
                  storeIntermediateToken <- auth.storeIntermediateToken(authTokens.consulNomadToken)
                  storeVaultTokenConfig <- auth.writeVaultTokenConfigs(
                    RadPath.persistentDir,
                    authTokens.consulNomadToken
                  )
                  storeConsulNomadTokenConfig <- auth.writeConsulNomadTokenConfigs(
                    RadPath.persistentDir,
                    authTokens.consulNomadToken,
                    authTokens.vaultToken
                  )
                  storeVaultToken <- IO(VaultUtils.storeVaultTokenKey("", authTokens.vaultToken))
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
                  storeVaultTokenConfig <- auth.writeVaultTokenConfigs(
                    RadPath.persistentDir,
                    authTokens.consulNomadToken
                  )
                  storeConsulNomadTokenConfig <- auth.writeConsulNomadTokenConfigs(
                    RadPath.persistentDir,
                    authTokens.consulNomadToken,
                    authTokens.vaultToken
                  )
                  storeVaultToken <- IO(VaultUtils.storeVaultTokenKey("", authTokens.vaultToken))
                } yield authTokens
            }
            // flags already written to consul by leader (first node)
            featureFlags <- if (!remoteJoin) for {
              featureFlags <- featureFlags.updateFlags(RadPath.persistentDir, Some(authTokens), confirm = true)(
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
            featureFlags <- featureFlags.updateFlags(RadPath.persistentDir, Some(authTokens))(serviceAddrs)
            _ <- startLogTuiAndRunTerraform(featureFlags, serviceAddrs, authTokens, waitForQuorum = false)
          } yield ()
          val handleFirewall =
            if (ensureSupported.osname == "windows") Util.addWindowsFirewallRules() else IO.unit
          val bootstrapIO = handleFirewall *> (if (remoteAddress.isDefined) remoteBootstrap else localBootstrap)
          val bootstrap = bootstrapIO.handleErrorWith(err => LogTUI.endTUI(Some(err)) *> IO(throw err)) *> LogTUI
            .endTUI()

          bootstrap.unsafeRunSync()
          sys.exit(0)

        case Stop =>
          (daemonutil.stopTerraform(integrationTest = false) *>
            Services.stopServices() *>
            IO(scribe.info("Stopped."))).unsafeRunSync
          sys.exit(0)

        case Update(remoteAddress, prefix, username, password) =>
          scribe.Logger.root
            .clearHandlers()
            .clearModifiers()
            .withHandler(scribe.handler.SynchronousLogHandler(writer = LogTUIWriter()))
            .replace()

          System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))

          val startTUIOnFlag = for {
            localFlags <- featureFlags.getLocalFlags(RadPath.persistentDir)
            _ <- if (localFlags.getOrElse("tui", true)) {
              for {
                nomadup <- Util.isPortUp(4646)
                vaultup <- Util.isPortUp(8200)
                _ <- LogTUI.startTUI(true, nomadup, vaultup) // Update is only run if consul is up
              } yield Unit
            } else IO.unit
          } yield Unit

          val consulExistsProc = for {
            serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
            authTokens <- auth.getAuthTokens(
              isRemote = remoteAddress.isDefined,
              serviceAddrs,
              username,
              password
            )
            flagMap <- featureFlags.updateFlags(RadPath.persistentDir, Some(authTokens))(serviceAddrs)
            _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
            _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
          } yield true

          Util
            .isPortUp(8501)
            .flatMap {
              case true =>
                for {
                  _ <- startTUIOnFlag *>
                    UpdateModules
                      .run(consulExistsProc, prefix = prefix)
                      .handleErrorWith(err => LogTUI.endTUI(Some(err))) *>
                    LogTUI.endTUI()
                } yield ()
              case false => IO(println("Consul is not up; cannot update"))
            }
            .unsafeRunSync()
          sys.exit(0)

        case dns: DNS =>
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

        case FlagSet(flagNames, enable, remoteAddress, username, password) =>
          scribe.Logger.root
            .clearHandlers()
            .clearModifiers()
            .withHandler(scribe.handler.SynchronousLogHandler(writer = LogTUIWriter()))
            .replace()

          System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
          val flagsToSet = flagNames.map((_, enable)).toMap

          val consulExistsProc = for {
            serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
            authTokens <- auth.getAuthTokens(isRemote = remoteAddress.isDefined, serviceAddrs, username, password)
            flagMap <- featureFlags.updateFlags(
              RadPath.persistentDir,
              Some(authTokens),
              flagsToSet,
              confirm = remoteAddress.isDefined
            )(serviceAddrs)
            _ <- daemonutil.runTerraform(flagMap)(serviceAddrs, authTokens) // calls updateFlagConfig
            _ <- if (remoteAddress.isEmpty) daemonutil.waitForQuorum(flagMap) else IO.unit
          } yield ()

          val noConsulProc = for {
            flagMap <- featureFlags.updateFlags(RadPath.persistentDir, None, flagsToSet)
            _ <- flagConfig.updateFlagConfig(flagMap)(persistentDir = RadPath.persistentDir)
            _ <- IO(scribe.warn("Could not connect to remote consul instance. Flags stored locally."))
          } yield ()

          IO(remoteAddress.isDefined || os.exists(RadPath.persistentDir / ".bootstrap-complete"))
            .flatMap {
              case true =>
                for {
                  consulup <- Util.isPortUp(8500)
                  nomadup <- Util.isPortUp(4646)
                  vaultup <- Util.isPortUp(8200)
                  flagMap <- featureFlags.getLocalFlags(RadPath.persistentDir)
                  _ <- if (flagMap.getOrElse("tui", true)) {
                    LogTUI.startTUI(consulup, nomadup, vaultup) *>
                      consulExistsProc.handleErrorWith(err => LogTUI.endTUI(Some(err))) *>
                      LogTUI.endTUI()
                  } else consulExistsProc
                } yield ()
              case false => noConsulProc
            }
            .unsafeRunSync()

          sys.exit(0)

        case FlagQuery(remoteAddress, username, password) =>
          System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
          val io = for {
            serviceAddrs <- if (remoteAddress.isDefined) daemonutil.getServiceIps() else IO.pure(ServiceAddrs())
            consulIsUp <- featureFlags.isConsulUp()(serviceAddrs)
            authTokens <- if (consulIsUp) {
              auth.getAuthTokens(isRemote = remoteAddress.isDefined, serviceAddrs, username, password).map(Some(_))
            } else IO.pure(None)
            _ <- featureFlags.printFlagInfo(RadPath.persistentDir, consulIsUp, serviceAddrs, authTokens)
          } yield ()
          io.unsafeRunSync()
          sys.exit(0)

        case AddUser(name, policies) =>
          if (!os.exists(RadPath.persistentDir / ".bootstrap-complete")) {
            Console.err.println("Please run timberland runtime start before adding a new user")
            sys.exit(1)
          }
          val policiesWithDefault = if (policies.nonEmpty) policies else List("remote-access")
          val vaultToken = VaultUtils.findVaultToken()
          implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
          val vault = new Vault[IO](Some(vaultToken), uri"https://127.0.0.1:8200")
          val proc = for {
            password <- IO(System.console.readPassword("Password> ").mkString)
            _ <- vault.enableAuthMethod("userpass")
            res <- vault.createUser(name, password, policiesWithDefault)
          } yield res match {
            case Right(_) =>
              scribe.info(s"Successfully added user $name to Vault.")
              sys.exit(0)
            case Left(err) =>
              scribe.error(s"$err")
              sys.exit(1)
          }
          proc.unsafeRunSync()

        case GoogleSheets =>
          val credentials = for {
            creds <- OAuthController.creds
            _ <- IO(scribe.info("Credentials successfully stored!"))
          } yield creds
          credentials.unsafeRunSync()
      }
    }

    try {
      cmdEval(execParser(args, "timberland", cli.opts))
    } catch {
      case os.SubprocessException(result) => sys.exit(result.exitCode)
    }
  }

}
