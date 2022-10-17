package com.radix.timberland.runtime

import cats.data._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import java.io.{File, FileWriter}
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import com.radix.timberland.runtime.Services.serviceController
import com.radix.timberland.flags.hooks.{awsAuthConfig, AWSAuthConfigFile}
import com.radix.timberland.radixdefs.ACLTokens
import com.radix.timberland.util._
import com.radix.utils.tls.ConsulVaultSSLContext
import org.http4s.Header
import org.http4s.Method.POST
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import io.circe.syntax._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import io.circe.generic.auto._
import io.circe.syntax._

trait ServiceControl {
  def restartConsul(): IO[Util.ProcOut] = ???
  def restartConsulTemplate(): IO[Util.ProcOut] = ???
  def startConsulTemplate(): IO[Util.ProcOut] = ???
  def restartNomad(): IO[Util.ProcOut] = ???
  def restartTimberlandSvc(): IO[Util.ProcOut] = ???
  def restartVault(): IO[Util.ProcOut] = ???
  def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def refreshVault()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def stopConsul(): IO[Util.ProcOut] = ???
  def stopConsulTemplate(): IO[Util.ProcOut] = ???
  def stopNomad(): IO[Util.ProcOut] = ???
  def stopTimberlandSvc(): IO[Util.ProcOut] = ???
  def stopVault(): IO[Util.ProcOut] = ???
  def configureConsul(parameters: String): IO[Unit] = ???
  def appendParametersConsul(parameters: String): IO[Unit] = ???
  def configureConsulTemplate(parameters: String): IO[Unit] = ???
  def configureConsulTemplate(consulToken: String, vaultToken: String, vaultAddress: Option[String]): IO[Unit] = ???
  def configureNomad(parameters: String, vaultToken: String, consulToken: String): IO[Unit] = ???
  def configureTimberlandSvc(): IO[Unit] = ???
  def configureVault(parameters: String): IO[Unit] = ???
}

class LinuxServiceControl extends ServiceControl {
  override def restartConsul(): IO[Util.ProcOut] = Util.exec("systemctl restart consul")
  override def restartConsulTemplate(): IO[Util.ProcOut] = Util.exec("systemctl restart consul-template")
  override def restartNomad(): IO[Util.ProcOut] = Util.exec("systemctl restart nomad")
  override def restartTimberlandSvc(): IO[Util.ProcOut] = Util.exec("systemctl restart timberland-svc")
  override def restartVault(): IO[Util.ProcOut] = Util.exec("systemctl restart vault")
  override def stopConsul(): IO[Util.ProcOut] = Util.exec("systemctl stop consul")
  override def stopNomad(): IO[Util.ProcOut] = Util.exec("systemctl stop nomad")
  override def stopVault(): IO[Util.ProcOut] = Util.exec("systemctl stop vault")
  override def stopTimberlandSvc(): IO[Util.ProcOut] = Util.exec("systemctl stop timberland-svc")

  override def configureConsul(parameters: String): IO[Unit] =
    for {
      consulArgStr <- IO.pure(s"CONSUL_CMD_ARGS=$parameters")
      envFilePath = Paths.get((RadPath.persistentDir / "consul" / "consul.env.conf").toString()) // TODO make configurable
      _ <- IO(os.write.over(os.Path(envFilePath), consulArgStr))
    } yield ()

  override def appendParametersConsul(parameters: String): IO[Unit] = {
    val consulEnvFilePath = RadPath.runtime / "timberland" / "consul" / "consul.env.conf"
    for {
      consulArgStr <- IO(os.read(consulEnvFilePath))
      _ <- IO(os.write.over(consulEnvFilePath, s"$consulArgStr $parameters"))
      _ <- serviceController.restartConsul()
    } yield ()
  }

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

  override def configureConsulTemplate(
    consulToken: String,
    vaultToken: String,
    vaultAddress: Option[String]
  ): IO[Unit] = {
    val envFilePath = RadPath.persistentDir / "consul-template" / "consul-template.env.conf"
    for {
      envars <- IO {
        s"""CONSUL_TEMPLATE_CMD_ARGS=-config=${(RadPath.persistentDir / "consul-template" / "config.hcl")
             .toString()} ${vaultAddress
             .map(addr => s"-vault-addr=https://$addr:8200")
             .getOrElse("")}
           |CONSUL_TOKEN=$consulToken
           |VAULT_TOKEN=$vaultToken
           |""".stripMargin
      }
      _ <- IO(os.write.over(envFilePath, envars))
    } yield ()
  }

  override def configureTimberlandSvc(): IO[Unit] = IO.unit

  override def startConsulTemplate(): IO[Util.ProcOut] = restartConsulTemplate()
  override def configureNomad(parameters: String, vaultToken: String, consulToken: String): IO[Unit] =
    for {
      args <- IO(s"""NOMAD_CMD_ARGS=$parameters -config=${RadPath.persistentDir}/nomad/config -consul-token=$consulToken
                             |VAULT_TOKEN=$vaultToken
                             |PATH=${sys.env("PATH")}:${(RadPath.persistentDir / "consul").toString()}
                             |""".stripMargin)

      _ = LogTUI.event(NomadStarting)
      _ = LogTUI.writeLog("spawning nomad via systemd")
      _ = os.write.over(RadPath.persistentDir / "nomad" / "nomad.env.conf", args)

    } yield ()

}

class WindowsServiceControl extends ServiceControl {
  implicit val timer: Timer[IO] = IO.timer(global)
  override def configureConsul(parameters: String): IO[Unit] =
    Util.execArr(
      "sc.exe create consul binpath="
        .split(" ") :+ s"${(RadPath.persistentDir / "consul" / "consul.exe").toString}"
    ) *>
      Util.execArr(
        "sc.exe config consul"
          .split(
            " "
          ) :+ "DisplayName=" :+ "Radix: Consul for Timberland" :+ "binPath=" :+ s"""\"${(RadPath.persistentDir / "consul" / "consul.exe").toString} agent $parameters -log-file ${(RadPath.persistentDir / "consul" / "consul.log").toString}\""""
      ) *> IO.unit

  override def appendParametersConsul(parameters: String): IO[Unit] =
    for {
      commandOut <- Util.exec("sc.exe qc consul 5000")
      origParameters <- IO(
        commandOut.stdout.split("\n").filter(line => line.contains("BINARY_PATH_NAME")).head.split(" : ").tail.head
      )
      _ <- configureConsul(s"$origParameters $parameters")
    } yield ()

  override def configureConsulTemplate(
    consulToken: String,
    vaultToken: String,
    vaultAddress: Option[String]
  ): IO[Unit] =
    Util.execArr(
      "sc.exe create consul-template binpath="
        .split(" ") :+ s"${(RadPath.persistentDir / "consul-template" / "consul-template.exe").toString}"
    ) *>
      Util.execArr(
        "sc.exe config consul-template"
          .split(
            " "
          ) :+ "DisplayName=" :+ "Radix: Consul-Template for Timberland" :+ "binPath=" :+ s"${(RadPath.persistentDir / "consul-template" / "consul-template.exe").toString} -config=${(RadPath.persistentDir / "consul-template" / "config-windows.hcl")
          .toString()} -vault-token=$vaultToken -consul-token=$consulToken -once ${vaultAddress
          .map(addr => s"-vault-addr=https://$addr:8200")
          .getOrElse("")}"
      ) *> IO.unit

  override def restartConsul(): IO[Util.ProcOut] = stopConsul *> startConsul

  override def stopConsul(): IO[Util.ProcOut] = Util.exec("sc.exe stop consul") *> flushDNS

  def startConsul(): IO[Util.ProcOut] = flushDNS *> Util.exec("sc.exe start consul")

  override def restartConsulTemplate(): IO[Util.ProcOut] = flushDNS *> stopConsulTemplate() *> startConsulTemplate()

  override def startConsulTemplate(): IO[Util.ProcOut] =
    flushDNS *>
      Util.exec("sc.exe start consul-template") *> refreshConsul

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

  override def configureNomad(parameters: String, vaultToken: String, consulToken: String): IO[Unit] =
    IO(
      os.copy.over(
        RadPath.runtime / "timberland" / "nomad" / "nomad-windows.hcl",
        RadPath.runtime / "timberland" / "nomad" / "config" / "nomad.hcl"
      )
    ) *>
      Util.execArr(
        "sc.exe create nomad binpath="
          .split(" ") :+ s"${(RadPath.persistentDir / "nomad" / "nomad.exe").toString}"
      ) *>
      Util.execArr(
        "sc.exe config nomad"
          .split(
            " "
          ) :+ "DisplayName=" :+ "Radix: Nomad for Timberland" :+ "binPath=" :+ s"${(RadPath.persistentDir / "nomad" / "nomad.exe").toString} agent $parameters  -config=${(RadPath.persistentDir / "nomad" / "config").toString} -vault-token=$vaultToken -consul-token=$consulToken -path=${sys
          .env("PATH")}:${(RadPath.persistentDir / "consul").toString()}:${RadPath.cni
          .toString()}:${sys.env.get("JAVA_HOME").map(os.Path(_) / "bin").getOrElse("").toString}"
        // include Consul, CNI, and JVM paths so Nomad will be able to find everything it needs
      ) *> IO.unit
  override def stopNomad(): IO[Util.ProcOut] = Util.exec("sc.exe stop nomad")

  def startNomad(): IO[Util.ProcOut] = Util.exec("sc.exe start nomad")
  override def restartNomad(): IO[Util.ProcOut] = stopNomad *> startNomad

  override def configureTimberlandSvc(): IO[Unit] = {
    val javaHome = sys.env.getOrElse("JAVA_HOME", {
      scribe.error("JAVA_HOME not set, can't find location of java.exe")
      sys.exit(1)
    })
    val javaLoc = (os.Path(javaHome) / "bin" / "java.exe").toString()
    val jarLoc = RadPath.persistentDir / "exec" / "timberland-svc-bin_deploy.jar"
    Util.execArr(
      "sc.exe create timberland-svc binpath="
        .split(" ") :+ javaLoc
    ) *>
      Util.execArr(
        "sc.exe config timberland-svc"
          .split(
            " "
          ) :+ "DisplayName=" :+ "Radix: Timberland Service" :+ "binPath=" :+ s"""\"$javaLoc -jar $jarLoc\""""
      ) *> IO.unit
  }

  def startTimberlandSvc(): IO[Util.ProcOut] = Util.exec("sc.exe start timberland-svc")
  override def stopTimberlandSvc(): IO[Util.ProcOut] = Util.exec("sc.exe stop timberland-svc")
  override def restartTimberlandSvc(): IO[Util.ProcOut] = stopTimberlandSvc *> startTimberlandSvc
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
        if (initialSetup && !os.exists(awsAuthConfig.configFile)) {
          os.write(awsAuthConfig.configFile, AWSAuthConfigFile(credsExistInVault = false).asJson.toString)
        }
      }
      _ <- IO {
        consulTemplateReplacementCerts.foreach { certBakPath =>
          if (os.exists(certBakPath)) os.remove(certBakPath)
        }
      }

      _ <- makeTempCerts(RadPath.persistentDir)
      _ <- if (initialSetup) auth.writeVaultTokenConfigs(RadPath.persistentDir, consulToken) else IO.unit

      // START VAULT
      _ <- if (!remoteJoin) for {
        _ <- IO { os.makeDir.all(RadPath.runtime / "vault") } // need to manually make dir so vault can make raft db
        _ <- startVault(finalBindAddr)
        _ <- (new VaultStarter).initializeAndUnsealAndSetupVault(initialSetup)
      } yield ()
      else IO.unit

      vaultToken <- IO((new VaultUtils).findVaultToken())
      _ <- if (initialSetup) auth.writeConsulNomadTokenConfigs(RadPath.persistentDir, consulToken, vaultToken)
      else IO.unit

      // START CONSUL TEMPLATE
      _ <- serviceController.configureConsulTemplate(consulToken, vaultToken, leaderNodeO)
      _ <- serviceController.startConsulTemplate()
      _ <- consulTemplateReplacementCerts.map(Util.waitForPathToExist(_, 30.seconds)).parSequence
      _ <- if (!remoteJoin) for {
        _ <- serviceController.restartVault()
        _ <- serviceController.restartConsulTemplate()
        _ <- IO.sleep(5.seconds)
        _ = ConsulVaultSSLContext.refreshCerts()
        _ <- (new VaultStarter)
          .initializeAndUnsealVault(baseUrl = uri"https://127.0.0.1:8200", shouldBootstrapVault = false)
      } yield ()
      else IO.unit

      // START CONSUL
      _ <- setupConsul(finalBindAddr, leaderNodeO, bootstrapExpect, clientJoin)
      _ <- Util.waitForSystemdString("consul", "agent: Synced node info", 60.seconds)
      actorToken <- if (initialSetup) auth.setupConsulTokens(RadPath.persistentDir, consulToken) else IO("")
      _ <- if (!remoteJoin) Util.waitForSystemdString("consul", "Synced service: service=vault:", 30.seconds)
      else IO.unit
      _ <- if (initialSetup) addConsulIntention(consulToken) else IO.unit

      // START NOMAD
      _ <- setupNomad(finalBindAddr, leaderNodeO, bootstrapExpect, vaultToken, consulToken, serverJoin)
      consulNomadToken <- if (initialSetup && !remoteJoin) {
        auth.setupNomadMasterToken(RadPath.persistentDir, consulToken)
      } else if (initialSetup && remoteJoin)
        IO(consulToken)
      else {
        auth.getMasterToken
      }
      _ <- if (!remoteJoin) auth.storeTokensInVault(ACLTokens(masterToken = consulNomadToken, actorToken = actorToken))
      else IO.unit
      _ <- if (serverJoin) serviceController.appendParametersConsul("-server") // add -server to consul invocation
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
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    serverJoin: Boolean = false
  ): IO[Unit] = {
    val baseArgs =
      s"""-bind=$bindAddr -advertise=$bindAddr -client=\\\"127.0.0.1 $bindAddr\\\" -config-dir=${(RadPath.persistentDir / "consul" / "config").toString}"""
    val clientJoin = leaderNodeO.isDefined && !serverJoin
    val remoteJoin = clientJoin || serverJoin

    val baseArgsWithSeeds = leaderNodeO match {
      case Some(seedString) =>
        seedString
          .split(',')
          .map { host =>
            s"-retry-join=$host"
          }
          .foldLeft(baseArgs) { (currentArgs, arg) =>
            currentArgs + ' ' + arg
          }

      case None => baseArgs
    }

    val baseArgsWithSeedsAndServer = remoteJoin match {
      case false => s"$baseArgsWithSeeds -bootstrap-expect=$bootstrapExpect -server"
      case true  => baseArgsWithSeeds
    }

    val consulConfigDir = RadPath.runtime / "timberland" / "consul" / "config"
    val consulDir = RadPath.runtime / "timberland" / "consul"
    val consulConfigFile = consulDir / (if (remoteJoin) "consul-client.json" else "consul-server.json")
    for {
      _ <- serviceController.configureConsul(baseArgsWithSeedsAndServer)
      _ <- IO(LogTUI.event(ConsulStarting))
      _ <- IO(os.copy.over(consulConfigFile, consulConfigDir / "consul.json"))
      _ <- serviceController.restartConsul()
      _ <- Util.waitForPortUp(8500, 10.seconds)
      _ <- Util.waitForPortUp(8501, 10.seconds)
      _ <- IO(LogTUI.event(ConsulSystemdUp))
    } yield ()
  }

//  def startNomad(bindAddr: String, bootstrapExpect: Int, vaultToken: String, remoteJoin: Boolean = false): IO[Unit] = {
  def setupNomad(
    bindAddr: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    vaultToken: String,
    consulToken: String,
    serverJoin: Boolean = false
  ): IO[Unit] = {
    val clientJoin = leaderNodeO.isDefined & !serverJoin
    val baseArgs = (clientJoin, serverJoin) match {
      case (false, false) => s"-bootstrap-expect=$bootstrapExpect -server"
      case (true, _)      => "-consul-client-auto-join"
      case (false, true)  => s"-server"
    }

    val baseArgsWithSeeds = leaderNodeO match {
      case Some(seedString) =>
        seedString
          .split(',')
          .map { host =>
            s"-retry-join=$host"
          }
          .foldLeft(baseArgs) { (currentArgs, arg) =>
            currentArgs + ' ' + arg
          }

      case None => baseArgs
    }
    val parameters: String = s"$baseArgsWithSeeds -bind=$bindAddr"
    for {
      configureNomad <- serviceController.configureNomad(parameters, vaultToken, consulToken)
      procOut <- serviceController.restartNomad()
      _ <- Util.waitForDNS("nomad.service.consul", 30.seconds).recoverWith(Function.unlift { _ =>
        scribe.warn("Nomad did not exit cleanly. Restarting nomad systemd service...")
        Some(serviceController.restartNomad().map(_ => ()))
      })
      _ <- IO(LogTUI.event(NomadSystemdUp))
    } yield ()
  }

  def startVault(bindAddr: String): IO[Unit] = {
    val args: String =
      s"""VAULT_CMD_ARGS=-address=https://${bindAddr}:8200 -config=${RadPath.persistentDir}/vault/vault_config.conf""".stripMargin
    for {
      _ <- IO {
        LogTUI.event(VaultStarting)
        LogTUI.writeLog("spawning vault via systemd")
        os.write.over(RadPath.persistentDir / "vault" / "vault.env.conf", args)
      }
      restartProc <- serviceController.restartVault()
      _ <- Util.waitForPortUp(8200, 30.seconds)
      _ <- IO(LogTUI.event(VaultSystemdUp))
    } yield ()

  }

  private def addConsulIntention(consulToken: String): IO[Unit] = {
    val req = POST(
      Map("SourceName" -> "*", "DestinationName" -> "*", "Action" -> "allow").asJson.toString(),
      uri"https://consul.service.consul:8501/v1/connect/intentions",
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

  def startWeave(hosts: List[String]): IO[Unit] =
    for {
      disableProc <- Util.exec("/usr/bin/docker plugin disable weaveworks/net-plugin:latest_release")
      setProc <- Util.exec("/usr/bin/docker plugin set weaveworks/net-plugin:latest_release IPALLOC_RANGE=10.32.0.0/12")
      enableProc <- Util.exec("/usr/bin/docker plugin disable weaveworks/net-plugin:latest_release")
    } yield ()

  def stopServices(): IO[Unit] =
    serviceController.stopNomad() *> serviceController.stopConsulTemplate() *> serviceController
      .stopVault() *> serviceController.stopNomad() *> IO.unit

}
