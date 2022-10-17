package com.radix.timberland.runtime

import cats.data._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import java.io.File
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import com.radix.timberland.runtime.Services.serviceController
import com.radix.timberland.radixdefs.ACLTokens
import com.radix.timberland.util._
import com.radix.utils.tls.{ConsulVaultSSLContext, TrustEveryoneSSLContext}
import org.http4s.{Header, Uri}
import org.http4s.Method.POST
import org.http4s.client.dsl.io._
import org.http4s.implicits._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import io.circe.generic.auto._
import io.circe.syntax._
import os.ProcessInput

trait ServiceControl {
  def restartConsul(): IO[Util.ProcOut] = ???
  def restartNomad(): IO[Util.ProcOut] = ???
  def restartTimberlandSvc(): IO[Util.ProcOut] = ???
  def restartVault(): IO[Util.ProcOut] = ???
  def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def refreshVault()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def stopConsul(): IO[Util.ProcOut] = ???
  def stopNomad(): IO[Util.ProcOut] = ???
  def stopTimberlandSvc(): IO[Util.ProcOut] = ???
  def stopVault(): IO[Util.ProcOut] = ???
  def configureConsul(parameters: List[String]): IO[Unit] = ???
  def runConsulTemplate(consulToken: String, vaultToken: String, vaultAddress: Option[String]): IO[Unit] = ???
  def configureNomad(parameters: List[String], vaultToken: String, consulToken: String): IO[Unit] = ???
  def configureTimberlandSvc(): IO[Unit] = ???
  def configureVault(parameters: String): IO[Unit] = ???
}

class LinuxServiceControl extends ServiceControl {
  override def restartConsul(): IO[Util.ProcOut] = Util.exec("systemctl restart consul")
  override def restartNomad(): IO[Util.ProcOut] = Util.exec("systemctl restart nomad")
  override def restartTimberlandSvc(): IO[Util.ProcOut] = Util.exec("systemctl restart timberland-svc")
  override def restartVault(): IO[Util.ProcOut] = Util.exec("systemctl restart vault")
  override def stopConsul(): IO[Util.ProcOut] = Util.exec("systemctl stop consul")
  override def stopNomad(): IO[Util.ProcOut] = Util.exec("systemctl stop nomad")
  override def stopVault(): IO[Util.ProcOut] = Util.exec("systemctl stop vault")
  override def stopTimberlandSvc(): IO[Util.ProcOut] = Util.exec("systemctl stop timberland-svc")

  override def configureConsul(parameters: List[String]): IO[Unit] =
    for {
      consulArgStr <- IO.pure(s"CONSUL_CMD_ARGS=${parameters.mkString(" ")}")
      envFilePath = Paths.get(
        (RadPath.persistentDir / "consul" / "consul.env.conf").toString()
      ) // TODO make configurable
      _ <- IO(os.write.over(os.Path(envFilePath), consulArgStr))
    } yield ()

  //Util.exec("systemctl reload consul")
  override def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] =
    for {
      consulPidRes <- Util.exec("/bin/systemctl show -p MainPID consul")
      consulPid = consulPidRes.stdout.split("=").last.strip
      hupProcOut <- Util.exec(s"kill -HUP $consulPid")
      _ <- IO.sleep(5.seconds)
      _ = ConsulVaultSSLContext.refreshCerts()
    } yield hupProcOut

  ///Util.exec("systemctl reload vault")
  override def refreshVault()(implicit timer: Timer[IO]): IO[Util.ProcOut] =
    for {
      vaultPidRes <- Util.exec("/bin/systemctl show -p MainPID vault")
      vaultPid = vaultPidRes.stdout.split("=").last.strip
      hupProcOut <- Util.exec(s"kill -HUP $vaultPid")
      _ <- IO.sleep(5.seconds)
      _ = ConsulVaultSSLContext.refreshCerts()
    } yield hupProcOut

  override def runConsulTemplate(
    consulToken: String,
    vaultToken: String,
    vaultAddress: Option[String]
  ): IO[Unit] =
    Util.execArr(
      List(
        (RadPath.persistentDir / "consul-template" / "consul-template").toString,
        "-once",
        "-config",
        (RadPath.persistentDir / "consul-template" / "config.hcl").toString,
        "-vault-token",
        vaultToken,
        "-consul-token",
        consulToken
      ) ++ vaultAddress.map(addr => List("-vault-addr", s"https://$addr:8200")).getOrElse(List.empty),
      spawn = true
    ) *> IO.unit

  override def configureTimberlandSvc(): IO[Unit] = IO.unit

  override def configureNomad(parameters: List[String], vaultToken: String, consulToken: String): IO[Unit] =
    for {
      args <- IO(s"""NOMAD_CMD_ARGS=${parameters
        .mkString(" ")} -config=${RadPath.persistentDir}/nomad/config -consul-token=$consulToken
                             |VAULT_TOKEN=$vaultToken
                             |PATH=${sys.env("PATH")}:${(RadPath.persistentDir / "consul").toString()}
                             |""".stripMargin)
      _ = scribe.info("spawning nomad via systemd")
      _ = os.write.over(RadPath.persistentDir / "nomad" / "nomad.env.conf", args)
      _ <- clearStaleRules
    } yield ()

  /**
   * Clears CNI-created ip routing rules from netfilter
   * Prevents this bug: https://github.com/hashicorp/nomad/issues/9558
   * If this isn't called, statically allocated ports and ingress gateways won't work
   */
  private def clearStaleRules: IO[Unit] =
    for {
      iptables <- Util.exec("iptables-save").map(_.stdout)
      cleanedTables = iptables
        .split("\n")
        .filter(!_.contains("CNI-"))
        .mkString("\n")
      _ <- IO {
        os.proc("iptables-restore").call(stdin = ProcessInput.makeSourceInput(cleanedTables))
      }
    } yield ()
}

class WindowsServiceControl extends ServiceControl {
  implicit val timer: Timer[IO] = IO.timer(global)
  private val nssm = RadPath.persistentDir / "nssm.exe"

  override def configureConsul(parameters: List[String]): IO[Unit] =
    Util.execArr(List(nssm.toString, "remove", "consul", "confirm")) *>
      IO.sleep(1.second) *> Util.execArr(
        (List(
          nssm.toString,
          "install",
          "consul",
          (RadPath.persistentDir / "consul" / "consul.exe").toString,
          "agent",
          "-log-file",
          (RadPath.persistentDir / "consul" / "consul.log").toString
        ) ++ parameters).map(_.replaceAll("\"", "\\\\\""))
      ) *> IO.unit

  override def runConsulTemplate(
    consulToken: String,
    vaultToken: String,
    vaultAddress: Option[String]
  ): IO[Unit] =
    Util.execArr(
      List(
        (RadPath.persistentDir / "consul-template" / "consul-template.exe").toString,
        "-once",
        "-config",
        (RadPath.persistentDir / "consul-template" / "config-windows.hcl").toString,
        "-vault-token",
        vaultToken,
        "-consul-token",
        consulToken
      ) ++ vaultAddress.map(addr => List("-vault-addr", s"https://$addr:8200")).getOrElse(List.empty),
      spawn = true
    ) *> IO.unit

  override def restartConsul(): IO[Util.ProcOut] = stopConsul *> IO.sleep(1.second) *> startConsul

  override def stopConsul(): IO[Util.ProcOut] = Util.exec(s"$nssm stop consul") *> flushDNS

  def startConsul(): IO[Util.ProcOut] = flushDNS *> Util.exec(s"$nssm start consul")

  def flushDNS(): IO[Util.ProcOut] = Util.exec("ipconfig.exe /flushdns")

  override def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] =
    for {
      _ <- stopConsul()
      _ <- IO.sleep(5.seconds)
      _ <- flushDNS
      procOut <- startConsul()
      _ <- IO.sleep(5.seconds)
      _ <- flushDNS
      _ <- IO(ConsulVaultSSLContext.refreshCerts())
    } yield procOut

  override def configureNomad(parameters: List[String], vaultToken: String, consulToken: String): IO[Unit] =
    IO(
      os.copy.over(
        RadPath.runtime / "timberland" / "nomad" / "nomad-windows.hcl",
        RadPath.runtime / "timberland" / "nomad" / "config" / "nomad.hcl"
      )
    ) *>
      Util.execArr(List(nssm.toString, "remove", "nomad", "confirm")) *>
      IO.sleep(1.second) *> Util.execArr(
        (List(
          nssm.toString,
          "install",
          "nomad",
          (RadPath.persistentDir / "nomad" / "nomad.exe").toString,
          "agent",
          s"""-config="${RadPath.persistentDir / "nomad" / "config"}"""",
          s"""-vault-token="$vaultToken"""",
          s"""-consul-token="$consulToken""""
        ) ++ parameters).map(_.replaceAll("\"", "\\\\\""))
      ) *> IO.unit
  override def stopNomad(): IO[Util.ProcOut] = Util.exec(s"$nssm stop nomad")

  def startNomad(): IO[Util.ProcOut] = Util.exec(s"$nssm start nomad")
  override def restartNomad(): IO[Util.ProcOut] = stopNomad *> IO.sleep(1.second) *> startNomad

  override def configureTimberlandSvc(): IO[Unit] = {
    val javaHome = sys.env.getOrElse(
      "JAVA_HOME", {
        scribe.error("JAVA_HOME not set, can't find location of java.exe")
        sys.exit(1)
      }
    )
    val javaLoc = (os.Path(javaHome) / "bin" / "java.exe").toString
    val jarLoc = RadPath.persistentDir / "exec" / "timberland-svc-bin_deploy.jar"
    Util.execArr(List(nssm.toString, "remove", "timberland-svc", "confirm")) *>
      IO.sleep(1.second) *> Util.execArr(
        List(
          nssm.toString,
          "install",
          "timberland-svc",
          javaLoc,
          "-jar",
          jarLoc.toString
        )
      ) *> IO.unit
  }

  def startTimberlandSvc(): IO[Util.ProcOut] = Util.exec(s"$nssm start timberland-svc")
  override def stopTimberlandSvc(): IO[Util.ProcOut] = Util.exec(s"$nssm stop timberland-svc")
  override def restartTimberlandSvc(): IO[Util.ProcOut] = stopTimberlandSvc *> IO.sleep(1.second) *> startTimberlandSvc
}
class DarwinServiceControl extends ServiceControl {}

object Services {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(256))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val timer: Timer[IO] = IO.timer(global)
  val bcs: ContextShift[IO] = IO.contextShift(ec)

  val serviceController = System.getProperty("os.name") match {
    case mac if mac.toLowerCase.contains("mac")             => new DarwinServiceControl
    case linux if linux.toLowerCase.contains("linux")       => new LinuxServiceControl
    case windows if windows.toLowerCase.contains("windows") => new WindowsServiceControl
  }

  /**
   * This method actually initializes the runtime.
   * It parses and rewrites default nomad and consul configuration, discovers peers, and
   * actually bootstraps and starts consul and nomad
   *
   * @param bindAddr  IPv4 to bind if not the default route
   * @param leaderNodeO  leader node, if applicable
   * @param bootstrapExpect   number of nodes to expect for bootstrapping
   * @param initialSetup whether or not we should setup the ACLs for consul/nomad/etc
   * @param serverJoin whether or not we should join as a server or client
   * @return a started consul and nomad
   */
  def startServices(
    bindAddr: Option[String],
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    initialSetup: Boolean,
    serverJoin: Boolean
  ): IO[AuthTokens] =
    for {
      // find local IP for default route
      finalBindAddr <- IO {
        bindAddr match {
          case Some(ip) => ip
          case None => {
            val sock = new java.net.DatagramSocket()
            sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
            sock.getLocalAddress.getHostAddress
          }
        }
      }
      certDir = os.root / "opt" / "radix" / "certs"
      consulTemplateReplacementCerts = List(
        certDir / "ca" / "cert.pem.bak",
        certDir / "vault" / "cert.pem.bak",
        certDir / "vault" / "key.pem.bak",
        certDir / "cli" / "cert.pem.bak",
        certDir / "cli" / "key.pem.bak"
      )

      intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
      clientJoin = (leaderNodeO.isDefined && !serverJoin)
      remoteJoin = clientJoin || serverJoin
      hasPartiallyBootstrapped <- IO(os.exists(intermediateAclTokenFile))
      consulToken <- makeOrGetIntermediateToken
      _ <- IO {
        consulTemplateReplacementCerts.foreach { certBakPath =>
          if (os.exists(certBakPath)) os.remove(certBakPath)
        }
      }

      _ <- makeTempCerts(RadPath.persistentDir)
      _ <- if (initialSetup) auth.writeVaultTokenConfigs(RadPath.persistentDir, consulToken) else IO.unit

      // START VAULT
      _ <-
        if (!remoteJoin) for {
          _ <- IO { os.makeDir.all(RadPath.runtime / "vault") } // need to manually make dir so vault can make raft db
          _ <- startVault(finalBindAddr)
          _ <- VaultStarter.initializeAndUnsealAndSetupVault(initialSetup)
        } yield ()
        else IO.unit

      vaultToken <- IO(VaultUtils.findVaultToken())
      _ <-
        if (initialSetup) auth.writeConsulNomadTokenConfigs(RadPath.persistentDir, consulToken, vaultToken)
        else IO.unit
      gkBlaze = if (remoteJoin) TrustEveryoneSSLContext.insecureBlaze else ConsulVaultSSLContext.blaze
      gossipKey <- auth.getGossipKey(vaultToken, leaderNodeO.getOrElse("127.0.0.1"), gkBlaze)

      // START CONSUL TEMPLATE
      _ <- serviceController.runConsulTemplate(consulToken, vaultToken, leaderNodeO)
      _ <- consulTemplateReplacementCerts.map(Util.waitForPathToExist(_, 30.seconds)).parSequence
      _ <-
        if (!remoteJoin) for {
          _ <- serviceController.restartVault()
          _ <- serviceController.runConsulTemplate(consulToken, vaultToken, leaderNodeO)
          _ <- IO.sleep(5.seconds)
          _ = ConsulVaultSSLContext.refreshCerts()
          _ <- VaultStarter
            .initializeAndUnsealVault(baseUrl = uri"https://127.0.0.1:8200", shouldBootstrapVault = false)
        } yield ()
        else IO(ConsulVaultSSLContext.refreshCerts())

      // START CONSUL
      _ <- setupConsul(finalBindAddr, gossipKey, leaderNodeO, bootstrapExpect, clientJoin)
      _ <- Util.waitForSystemdString("consul", "agent: Synced node info", 60.seconds)
      actorToken <- if (initialSetup) auth.setupConsulTokens(RadPath.persistentDir, consulToken) else IO("")
      _ <-
        if (!remoteJoin) Util.waitForSystemdString("consul", "Synced service: service=vault:", 30.seconds)
        else IO.unit
      _ <- if (initialSetup) addConsulIntention(consulToken) else IO.unit

      // START NOMAD
      _ <- setupNomad("0.0.0.0", gossipKey, leaderNodeO, bootstrapExpect, vaultToken, consulToken, serverJoin)
      _ <- Util.waitForPortUp(4646, 30.seconds)
      consulNomadToken <-
        if (initialSetup && !remoteJoin) {
          auth.setupNomadMasterToken(RadPath.persistentDir, consulToken)
        } else if (initialSetup && remoteJoin)
          IO(consulToken)
        else {
          auth.getMasterToken
        }
      _ <- if (initialSetup) addNomadNamespace(consulNomadToken) else IO.unit
      _ <-
        if (!remoteJoin) auth.storeTokensInVault(ACLTokens(masterToken = consulNomadToken, actorToken = actorToken))
        else IO.unit
      _ <- IO(os.write.over(RadPath.persistentDir / ".bootstrap-complete", "\n"))
      _ <- IO(if (os.exists(intermediateAclTokenFile)) {
        val intermediateFile = new File(intermediateAclTokenFile.toString())
        intermediateFile.setWritable(true, true)
        os.remove(intermediateAclTokenFile)
      } else ())
      _ <- serviceController.configureTimberlandSvc()
      _ <- serviceController.restartTimberlandSvc()
      _ <- IO(scribe.info("started services"))
    } yield AuthTokens(consulNomadToken = consulNomadToken, actorToken = actorToken, vaultToken)

  def searchForPort(netinf: List[String], port: Int): IO[Option[NonEmptyList[String]]] = {
    val addrs = for {
      last <- 0 to 254
      octets <- netinf
    } yield IO {
      Try {
        val s = new Socket()
        s.connect(new InetSocketAddress(octets + last, port), 200)
        s.close()
      } match {
        case Success(_) => Some(octets + last)
        case Failure(_) => None
      }
    }
    IO.shift(bcs) *> addrs.toList.parSequence.map(_.flatten).map(NonEmptyList.fromList) <* IO.shift
  }

  def setupConsul(
    bindAddr: String,
    gossipKey: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    serverJoin: Boolean = false
  ): IO[Unit] = {
    val baseArgs = List(
      s"""-bind="$bindAddr"""",
      s"""-advertise="$bindAddr"""",
      s"""-client="127.0.0.1 $bindAddr"""",
      s"""-config-dir="${(RadPath.persistentDir / "consul" / "config").toString}"""",
      s"""-encrypt="$gossipKey""""
    )
    val clientJoin = leaderNodeO.isDefined && !serverJoin
    val remoteJoin = clientJoin || serverJoin

    val baseArgsWithSeeds = leaderNodeO match {
      case Some(seedString) =>
        baseArgs ++ seedString
          .split(',')
          .map { host =>
            s"""-retry-join="$host""""
          }

      case None => baseArgs
    }

    val baseArgsWithSeedsAndServer = remoteJoin match {
      case false => baseArgsWithSeeds ++ List(s"""-bootstrap-expect="$bootstrapExpect"""", "-server")
      case true  => baseArgsWithSeeds
    }

    val consulConfigDir = RadPath.runtime / "timberland" / "consul" / "config"
    val consulDir = RadPath.runtime / "timberland" / "consul"
    val consulConfigFile = consulDir / (if (remoteJoin) "consul-client.json" else "consul-server.json")
    for {
      _ <- serviceController.configureConsul(baseArgsWithSeedsAndServer)
      _ <- IO(os.copy.over(consulConfigFile, consulConfigDir / "consul.json"))
      _ <- serviceController.restartConsul()
      _ <- Util.waitForPortUp(8500, 10.seconds)
      _ <- Util.waitForPortUp(8501, 10.seconds)
    } yield ()
  }

  def setupNomad(
    bindAddr: String,
    gossipKey: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    vaultToken: String,
    consulToken: String,
    serverJoin: Boolean = false
  ): IO[Unit] = {
    val clientJoin = leaderNodeO.isDefined & !serverJoin
    val baseArgs = (clientJoin, serverJoin) match {
      case (false, false) => List(s"""-bootstrap-expect="$bootstrapExpect"""", "-server")
      case (true, _)      => List("-consul-client-auto-join")
      case (false, true)  => List(s"-server")
    }

    val baseArgsWithSeeds = leaderNodeO match {
      case Some(seedString) =>
        baseArgs ++ seedString
          .split(',')
          .map { host =>
            s"""-retry-join="$host""""
          }
      case None => baseArgs
    }
    val parameters = baseArgsWithSeeds ++ List(s"""-bind="$bindAddr"""", s"""-encrypt="$gossipKey"""")
    for {
      configureNomad <- serviceController.configureNomad(parameters, vaultToken, consulToken)
      procOut <- serviceController.restartNomad()
      _ <- Util
        .waitForServiceDNS("nomad", 30.seconds)
        .recoverWith(Function.unlift { _ =>
          scribe.warn("Nomad did not exit cleanly. Restarting nomad systemd service...")
          Some(serviceController.restartNomad().map(_ => false)) // not sure if that's right
        })
    } yield ()
  }

  def startVault(bindAddr: String): IO[Unit] = {
    val args: String =
      s"""VAULT_CMD_ARGS=-address=https://${bindAddr}:8200 -config=${RadPath.persistentDir}/vault/vault_config.conf""".stripMargin
    for {
      _ <- IO {
        scribe.info("spawning vault via systemd")
        os.write.over(RadPath.persistentDir / "vault" / "vault.env.conf", args)
      }
      restartProc <- serviceController.restartVault()
      _ <- Util.waitForPortUp(8200, 30.seconds)
    } yield ()

  }

  private def addNomadNamespace(aclToken: String): IO[Unit] = {
    for {
      namespaceRaw <- IO(os.read(RadPath.runtime / "timberland" / "release-name.txt"))
      namespace = namespaceRaw.stripSuffix("\n")
      req = POST(
        Map("Name" -> namespace).asJson.toString(),
        Uri.unsafeFromString("https://127.0.0.1:4646/v1/namespace/" + namespace),
        Header("X-Nomad-Token", aclToken)
      )
      status <- ConsulVaultSSLContext.blaze.use(_.status(req))
      _ <- IO {
        if (status.code != 200) scribe.warn("Error adding nomad namespace: " + status.reason)
      }
    } yield ()
  }

  private def addConsulIntention(consulToken: String): IO[Unit] = {
    val req = POST(
      Map("SourceName" -> "*", "DestinationName" -> "*", "Action" -> "allow").asJson.toString(),
      uri"http://consul.service.consul:8500/v1/connect/intentions",
      Header("X-Consul-Token", consulToken)
    )

    ConsulVaultSSLContext.blaze.use(_.status(req)).map { status =>
      if (status.code != 200) scribe.warn("Error adding consul intention: " + status.reason)
      ()
    }
  }

  private def makeOrGetIntermediateToken: IO[String] =
    IO {
      val intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
      if (os.exists(intermediateAclTokenFile)) {
        os.read(intermediateAclTokenFile)
      } else {
        val token = UUID.randomUUID().toString
        os.write(intermediateAclTokenFile, token, os.PermSet(400))
        token
      }
    }

  /**
   * This creates temporary certificates so that vault can start before it is configured to generate its own certs
   */
  private def makeTempCerts(persistentDir: os.Path): IO[Unit] = {
    implicit val certDir: os.Path = RadPath.runtime / "certs"
    val consul = persistentDir / "consul" / "consul"

    IO(os.makeDir.all(certDir)) *>
      Util.exec(s"$consul tls ca create", cwd = certDir) *>
      genCert(
        folder = "vault",
        fileName = "dc1-client-consul-0",
        command = Some(s"$consul tls cert create -client -additional-dnsname=vault.service.consul")
      ) *>
      genCert(
        folder = "cli",
        fileName = "dc1-cli-consul-0",
        command = Some(s"$consul tls cert create -cli")
      ) *>
      genCert(
        folder = "consul",
        fileName = "dc1-server-consul-0",
        command = Some(s"$consul tls cert create -server -additional-dnsname=consul.service.consul")
      ) *>
      genCert(
        folder = "ca",
        fileName = "consul-agent-ca"
      ) *>
      IO(os.remove(certDir / "ca" / "key.pem"))
  }

  /**
   * This method runs $command and moves the resulting files $fileName.pem and $fileName-key.pem to radix/$Folder
   * @param folder The folder to put the resulting cert and key in
   * @param fileName The name of the cert file generated by running $command
   * @param command The command to run to generate the required cert and key
   */
  private def genCert(folder: String, fileName: String, command: Option[String] = None)(implicit certDir: os.Path) =
    command.map(Util.exec(_, certDir)).getOrElse(IO.unit) *> IO {
      os.makeDir.all(certDir / folder)
      os.move.over(certDir / s"$fileName.pem", certDir / folder / "cert.pem")
      os.move.over(certDir / s"$fileName-key.pem", certDir / folder / "key.pem")
    }

  def stopServices(): IO[Unit] =
    serviceController.stopNomad() *> serviceController.stopVault() *> serviceController.stopNomad() *> IO.unit

}
