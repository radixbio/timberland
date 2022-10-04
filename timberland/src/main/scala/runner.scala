package com.radix.timberland

import flags.hooks.ensureSupported
import flags.{configGen, featureFlags}
import launch.daemonutil
import radixdefs.ServiceAddrs
import runtime._
import util.{RadPath, Util, VaultStarter, VaultUtils}

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.utils.helm.http4s.Http4sConsulClient
import com.radix.utils.helm.http4s.vault.Vault
import com.radix.utils.tls.ConsulVaultSSLContext._
import com.radix.utils.tls.{ConsulVaultSSLContext, TrustEveryoneSSLContext}
import io.circe.syntax.EncoderOps
import io.circe.{Parser => _}
import optparse_applicative._
import org.http4s.Uri
import org.http4s.implicits._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object runner {

  def main(args: Array[String]): Unit = {

    def cmdEval(cmd: RadixCMD): Unit = {
      cmd match {
        case cmd @ Start(
              loglevel,
              bindIP,
              leaderNode,
              remoteAddress,
              namespace,
              datacenter,
              username,
              password,
              serverJoin,
              force
            ) =>
          scribe.Logger.root
            .clearHandlers()
            .clearModifiers()
            .withHandler(minimumLevel = Some(loglevel))
            .replace()
          scribe.debug(s"starting runtime with $cmd")

          System.setProperty("dns.server", remoteAddress.getOrElse("127.0.0.1"))
          // disable DNS cache
          System.setProperty("networkaddress.cache.ttl", "0")

          def startServices(setupACL: Boolean) =
            for {
              _ <- IO(scribe.debug("Launching daemons"))
              flags <- featureFlags.flags
              bootstrapExpect = if (flags.getOrElse("dev", false)) 1 else 3
              tokens <- Services.startServices(bindIP, leaderNode, datacenter, bootstrapExpect, setupACL, serverJoin)
              _ <-
                if (leaderNode.isEmpty) {
                  Util.waitForPortUp(8200, 30.seconds)
                } else {
                  IO(scribe.info("Not waiting for Vault to be up since leader node is running Vault already"))
                }
            } yield tokens

          // only run on leader node, or with a remote address as a target
          def runTerraform(
            serviceAddrs: ServiceAddrs,
            authTokens: AuthTokens
          ) =
            for {
              _ <-
                if (force) {
                  implicit val blaze = ConsulVaultSSLContext.blaze
                  val consulUri = Uri.unsafeFromString(serviceAddrs.consulAddr)
                  val consul = new Http4sConsulClient[IO](consulUri, Some(authTokens.consulNomadToken))
                  List("terraform/.lock", "terraform/.lockinfo", "terraform").map(consul.kvDelete).sequence
                } else IO.unit
              tfStatus <- daemonutil.runTerraform(namespace, datacenter)(serviceAddrs, authTokens)
              _ <- IO {
                tfStatus match {
                  case 0 => true
                  case code =>
                    scribe.error("runTerraform failed, exiting")
                    sys.exit(code)
                }
              }
            } yield ()

          // Called when consul/nomad/vault are on localhost
          val localBootstrap = for {
            _ <- IO(
              if (!os.exists(RadPath.consulExec / os.up / "config"))
                os.makeDir(RadPath.consulExec / os.up / "config")
              else ()
            )
            hasBootstrapped <- IO(os.exists(ConstPaths.bootstrapComplete))
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
              case (true, _, true) =>
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
            _ <- runTerraform(defaultServiceAddrs, authTokens)
          } yield ()

          // Called when consul/vault/nomad are not on localhost
          val remoteBootstrap = for {
            serviceAddrs <- daemonutil.getServiceIps
            authTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
            _ <- runTerraform(serviceAddrs, authTokens)
          } yield ()
          val handleFirewall =
            if (ensureSupported.osname == "windows") Util.addWindowsFirewallRules() else IO.unit

          val saveArgs = IO(os.write.over(ConstPaths.argFile, cmd.asJson.toString()))

          val bootstrapIO = {
            saveArgs *>
              featureFlags.generateAllTfAndConfigFiles *>
              handleFirewall *>
              (if (remoteAddress.isDefined) remoteBootstrap else localBootstrap)
          }
          val bootstrap = bootstrapIO.handleErrorWith(err => IO(scribe.error(err)))

          bootstrap.unsafeRunSync()
          sys.exit(0)

        case Stop =>
          (for {
            tokens <- auth.getAuthTokens()
            _ <- daemonutil.runTerraform(shouldStop = true)(tokens = tokens) // TODO: add dc
            _ <- Services.stopServices()
            _ <- IO(scribe.info("Stopped."))
          } yield ()).unsafeRunSync
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

        case MakeConfig =>
          featureFlags.generateAllTfAndConfigFiles.unsafeRunSync()

        case FlagConfig(flags, all, remoteAddress, username, password) =>
          if (flags.isEmpty || flags.contains("all")) {
            configGen.setAllConfigValues(onlyMissing = !all).unsafeRunSync()
          } else {
            flags.map(configGen.setConfigValues(_, onlyMissing = !all)).sequence.unsafeRunSync()
          }
          if (os.exists(ConstPaths.bootstrapComplete)) {
            cmdEval(Start(remoteAddress = remoteAddress, username = username, password = password))
          }

        case FlagSet(flagNames, enable, remoteAddress, username, password) =>
          featureFlags.setFlags(flagNames, enable).unsafeRunSync()
          featureFlags.generateAllTfAndConfigFiles.unsafeRunSync()
          if (os.exists(ConstPaths.bootstrapComplete)) {
            cmdEval(Start(remoteAddress = remoteAddress, username = username, password = password))
          }

        case FlagQuery => featureFlags.query.unsafeRunSync()

        case WanJoin(host, leaderNode, username, password, publicAddr) =>
          if (host) {
            wan.prepareWanHost(publicAddr).unsafeRunSync()
          } else {
            wan.wanJoin(leaderNode, username, password, publicAddr).unsafeRunSync()
          }
          sys.exit(0)

        case AddUser(name, policies) =>
          if (!os.exists(ConstPaths.bootstrapComplete)) {
            scribe.error("Please run timberland runtime start before adding a new user")
            sys.exit(1)
          }
          val policiesWithDefault = if (policies.nonEmpty) policies else List("remote-access")
          val vaultToken = VaultUtils.findVaultToken()
          implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
          val vault = new Vault[IO](vaultToken, uri"https://127.0.0.1:8200")
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

        case Env(fish) =>
          val aclToken = os.read(RadPath.persistentDir / ".acl-token")

          //something smells around here
          for (envVar <- EnvironmentVariables.envToken(aclToken)) if (fish) {
            println(s"set ${envVar._1} ${envVar._2};")
          } else {
            println(s"export ${envVar._1}=${envVar._2}")
          }
          sys.exit(0)

        case Reload =>
          Services.stopServices().unsafeRunSync()
          cmdEval(AfterStartup)

        case AfterStartup =>
          if (!os.exists(ConstPaths.bootstrapComplete)) {
            scribe.error("Timberland not yet initialized")
            sys.exit(1)
          }
          implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
          implicit val timer: Timer[IO] = IO.timer(global)
          val restartVaultAndGetTokens = for {
            _ <- Services.serviceController.restartVault()
            _ <- Util.waitForPortUp(8200, 30.seconds)
            _ <- VaultStarter.initializeAndUnsealVault(
              baseUrl = Uri.unsafeFromString("https://127.0.0.1:8200"),
              shouldBootstrapVault = false,
              vaultBlaze = TrustEveryoneSSLContext.insecureBlaze
            )
            _ <- IO.sleep(10.seconds) // This works on line 482 of VaultStarter.scala, so it should work here
            consulNomadToken <- auth.getMasterToken
            vaultToken <- IO(os.read(RadPath.runtime / "timberland" / ".vault-token"))
          } yield AuthTokens(consulNomadToken, "", vaultToken)
          val prog = for {
            args <- auth.recoverArgs
            // If vault is installed on local computer, restart it, unseal it, and get tokens from local file
            // If vault is not installed, just query the remote vault for tokens
            hasLocalVault = args.leaderNode.isEmpty || args.serverMode
            tokens <- if (hasLocalVault) restartVaultAndGetTokens else auth.recoverRunContext(args).map(_._2)
            vaultAddr = if (hasLocalVault) None else args.leaderNode.map(host => s"https://$host:8200")
            _ <- Services.serviceController.runConsulTemplate(
              tokens.consulNomadToken,
              tokens.vaultToken,
              vaultAddr,
              args.datacenter
            )
            _ <- Services.serviceController.restartConsul()
            _ <- Util.waitForPortUp(8501, 30.seconds)
            _ <- Services.serviceController.restartNomad()
            _ <- Services.serviceController.restartTimberlandSvc()
          } yield ()
          prog.unsafeRunSync()
      }
    }

    try {
      cmdEval(execParser(args, "timberland", cli.opts))
    } catch {
      case os.SubprocessException(result) => sys.exit(result.exitCode)
    }
  }

}
