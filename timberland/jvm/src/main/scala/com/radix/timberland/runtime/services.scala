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
import com.radix.timberland.util._
import com.radix.utils.tls.ConsulVaultSSLContext

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

trait ServiceControl {
  def restartConsul(): IO[Util.ProcOut] = ???
  def restartConsulTemplate(): IO[Util.ProcOut] = ???
  def startConsulTemplate(): IO[Util.ProcOut] = ???
  def restartNomad(): IO[Util.ProcOut] = ???
  def restartVault(): IO[Util.ProcOut] = ???
  def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def refreshVault()(implicit timer: Timer[IO]): IO[Util.ProcOut] = ???
  def stopConsul(): IO[Util.ProcOut] = ???
  def stopConsulTemplate(): IO[Util.ProcOut] = ???
  def stopNomad(): IO[Util.ProcOut] = ???
  def stopVault(): IO[Util.ProcOut] = ???
  def configureConsul(parameters: String): IO[Unit] = ???
  def appendParametersConsul(parameters: String): IO[Unit] = ???
  def configureConsulTemplate(parameters: String): IO[Unit] = ???
  def configureConsulTemplate(consulToken: String, vaultToken: String): IO[Unit] = ???
  def configureNomad(parameters: String, vaultToken: String): IO[Unit] = ???
  def configureVault(parameters: String): IO[Unit] = ???
}

class LinuxServiceControl extends ServiceControl {
  override def restartConsul(): IO[Util.ProcOut] = Util.exec("systemctl restart consul")
  override def restartConsulTemplate(): IO[Util.ProcOut] = Util.exec("systemctl restart consul-template")
  override def restartNomad(): IO[Util.ProcOut] = Util.exec("systemctl restart nomad")
  override def restartVault(): IO[Util.ProcOut] = Util.exec("systemctl restart vault")
  override def stopConsul(): IO[Util.ProcOut] = Util.exec("systemctl stop consul")
  override def stopNomad(): IO[Util.ProcOut] = Util.exec("systemctl stop nomad")
  override def stopVault(): IO[Util.ProcOut] = Util.exec("systemctl stop vault")

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

  override def configureConsulTemplate(consulToken: String, vaultToken: String): IO[Unit] = {
    val envFilePath = RadPath.persistentDir / "consul-template" / "consul-template.env.conf"
    for {
      envars <- IO {
        s"""CONSUL_TEMPLATE_CMD_ARGS=-config=${(RadPath.persistentDir / "consul-template" / "config.hcl").toString()}
             |CONSUL_TOKEN=$consulToken
             |VAULT_TOKEN=$vaultToken
             |""".stripMargin
      }
      _ <- IO(os.write.over(envFilePath, envars))
    } yield ()
  }

  override def startConsulTemplate(): IO[Util.ProcOut] = restartConsulTemplate()
  override def configureNomad(parameters: String, vaultToken: String): IO[Unit] =
    for {
      args <- IO(s"""NOMAD_CMD_ARGS=$parameters -config=${RadPath.persistentDir}/nomad/config
                             |VAULT_TOKEN=$vaultToken
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
          ) :+ "DisplayName=" :+ "Consul for Radix Timberland" :+ "binPath=" :+ s"""\"${(RadPath.persistentDir / "consul" / "consul.exe").toString} agent $parameters -log-file ${(RadPath.persistentDir / "consul" / "consul.log").toString}\""""
      ) *> IO.unit

  override def appendParametersConsul(parameters: String): IO[Unit] =
    for {
      commandOut <- Util.exec("sc.exe qc consul 5000")
      origParameters <- IO(
        commandOut.stdout.split("\n").filter(line => line.contains("BINARY_PATH_NAME")).head.split(" : ").tail.head
      )
      _ <- configureConsul(s"$origParameters $parameters")
    } yield ()

  override def configureConsulTemplate(consulToken: String, vaultToken: String): IO[Unit] =
    Util.execArr(
      "sc.exe create consul-template binpath="
        .split(" ") :+ s"${(RadPath.persistentDir / "consul-template" / "consul-template.exe").toString}"
    ) *>
      Util.execArr(
        "sc.exe config consul-template"
          .split(
            " "
          ) :+ "DisplayName=" :+ "Consul-Template for Radix Timberland" :+ "binPath=" :+ s"${(RadPath.persistentDir / "consul-template" / "consul-template.exe").toString} -config=${(RadPath.persistentDir / "consul-template" / "config-windows.hcl")
          .toString()} -vault-token=$vaultToken -consul-token=$consulToken"
      ) *> IO.unit

  override def restartConsul(): IO[Util.ProcOut] = stopConsul *> startConsul

  override def stopConsul(): IO[Util.ProcOut] = Util.exec("sc.exe stop consul")

  def startConsul(): IO[Util.ProcOut] = Util.exec("sc.exe start consul")

  override def startConsulTemplate(): IO[Util.ProcOut] =
    Util.exec("sc.exe start consul-template") *> refreshConsul *> restartNomad

  override def refreshConsul()(implicit timer: Timer[IO]): IO[Util.ProcOut] =
    for {
      _ <- stopConsul()
      procOut <- startConsul()
      _ <- IO.sleep(5.seconds)
      _ <- IO(ConsulVaultSSLContext.refreshCerts())
    } yield procOut

  override def configureNomad(parameters: String, vaultToken: String): IO[Unit] =
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
          ) :+ "DisplayName=" :+ "Nomad for Radix Timberland" :+ "binPath=" :+ s"${(RadPath.persistentDir / "nomad" / "nomad.exe").toString} agent $parameters  -config=${(RadPath.persistentDir / "nomad" / "config").toString} -vault-token=$vaultToken"
      ) *> IO.unit
  override def stopNomad(): IO[Util.ProcOut] = Util.exec("sc.exe stop nomad")

  def startNomad(): IO[Util.ProcOut] = Util.exec("sc.exe start nomad")
  override def restartNomad(): IO[Util.ProcOut] = stopNomad *> startNomad

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
   * This method actually initializes the runtime given a runtime algebra executor.
   * It parses and rewrites default nomad and consul configuration, discovers peers, and
   * actually bootstraps and starts consul and nomad
   *
   * @param consulwd  what's the working directory where we can find the consul configuration and executable binary
   * @param nomadwd   what's the working directory where we can find the nomad configuration and executable binary
   * @param bindAddr are we binding to a specific host IP?
   * @return a started consul and nomad
   */
  def startServices(
    bindAddr: Option[String],
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    setupACL: Boolean,
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
      hasPartiallyBootstrapped <- IO(os.exists(intermediateAclTokenFile))
      consulToken <- makeOrGetIntermediateToken
      clientJoin = (leaderNodeO.isDefined && !serverJoin)
      remoteJoin = clientJoin | serverJoin
      _ <- if (setupACL) auth.writeTokenConfigs(RadPath.persistentDir, consulToken) else IO.unit
      _ <- IO {
        consulTemplateReplacementCerts.foreach { certBakPath =>
          if (os.exists(certBakPath)) os.remove(certBakPath)
        }
      }
      _ <- setupConsul(finalBindAddr, leaderNodeO, bootstrapExpect, clientJoin)
      _ <- if (setupACL) auth.setupDefaultConsulToken(RadPath.persistentDir, consulToken) else IO.unit
      _ <- if (!remoteJoin) for {
        _ <- startVault(finalBindAddr)
        _ <- (new VaultStarter).initializeAndUnsealAndSetupVault(setupACL)
        _ <- Util.waitForSystemdString("consul", "Synced service: service=vault:", 30.seconds)
      } yield ()
      else IO.unit
      vaultToken <- IO((new VaultUtils).findVaultToken())
      _ <- serviceController.configureConsulTemplate(consulToken, vaultToken) // wait for consulCaCertPemBak
      _ <- serviceController.startConsulTemplate()
      _ <- consulTemplateReplacementCerts.map(Util.waitForPathToExist(_, 30.seconds)).parSequence
      _ <- if (remoteJoin)
        IO(
          os.copy.over(
            RadPath.runtime / "timberland" / "consul" / "consul-client-tls.json",
            RadPath.runtime / "timberland" / "consul" / "config" / "consul.json"
          )
        )
      else IO.unit
      _ <- serviceController.refreshConsul()
      _ <- if (!remoteJoin) serviceController.refreshVault() else IO.unit
      _ <- setupNomad(finalBindAddr, leaderNodeO, bootstrapExpect, vaultToken, serverJoin)
      consulNomadToken <- if (setupACL & !remoteJoin) {
        auth.setupNomadMasterToken(RadPath.persistentDir, consulToken)
      } else if (setupACL & remoteJoin)
        IO(consulToken)
      else {
        auth.getMasterToken
      }
      _ <- if (!remoteJoin) auth.storeMasterToken(consulNomadToken) else IO.unit
      _ <- if (serverJoin) serviceController.appendParametersConsul("-server") // add -server to consul invocation
      else IO.unit
      _ <- IO(os.write.over(RadPath.persistentDir / ".bootstrap-complete", "\n"))
      _ <- IO(if (os.exists(intermediateAclTokenFile)) {
        val intermediateFile = new File(intermediateAclTokenFile.toString())
        intermediateFile.setWritable(true, true)
        os.remove(intermediateAclTokenFile)
      } else ())
      _ <- IO(scribe.info("started services"))
    } yield AuthTokens(consulNomadToken, vaultToken)

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
    val remoteJoin = clientJoin | serverJoin

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
    remoteJoin match {
      case false =>
        for {
          _ <- serviceController.configureConsul(baseArgsWithSeedsAndServer)
          _ <- IO(LogTUI.event(ConsulStarting))
          _ <- IO(os.copy.over(consulDir / "consul-server-bootstrap.json", consulConfigDir / "consul.json"))
          _ <- serviceController.restartConsul()
          _ <- Util.waitForPortUp(8500, 10.seconds)
          _ <- makeTempCerts(RadPath.persistentDir)
          _ <- IO(os.copy.over(consulDir / "consul-server.json", consulConfigDir / "consul.json"))
          _ <- serviceController.restartConsul()
          _ <- IO(LogTUI.event(ConsulSystemdUp))
          _ <- Util.waitForPortUp(8501, 10.seconds)
        } yield ()
      case true =>
        for {
          _ <- serviceController.configureConsul(baseArgsWithSeedsAndServer)
          _ <- IO(LogTUI.event(ConsulStarting))
          _ <- IO(os.copy.over(consulDir / "consul-client.json", consulConfigDir / "consul.json"))
          _ <- serviceController.restartConsul()
          _ <- Util.waitForPortUp(8500, 10.seconds)
          _ <- Util.waitForPortUp(8501, 10.seconds)
          _ <- makeTempCerts(RadPath.persistentDir)
          _ <- IO(LogTUI.event(ConsulSystemdUp))
        } yield ()
    }
  }

//  def startNomad(bindAddr: String, bootstrapExpect: Int, vaultToken: String, remoteJoin: Boolean = false): IO[Unit] = {
  def setupNomad(
    bindAddr: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    vaultToken: String,
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
      configureNomad <- serviceController.configureNomad(parameters, vaultToken)
      procOut <- serviceController.restartNomad()
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

  private def makeTempCerts(persistentDir: os.Path): IO[Unit] = {
    val consul = persistentDir / "consul" / "consul"
    val certDir = os.root / "opt" / "radix" / "certs"
    val consulCaCertPem = certDir / "ca" / "cert.pem"
    val consulCaKeyPem = certDir / "ca" / "key.pem"
    val consulServerCertPem = certDir / "consul" / "cert.pem"
    val consulServerKeyPem = certDir / "consul" / "key.pem"
    val vaultClientCertPem = certDir / "vault" / "cert.pem"
    val vaultClientKeyPem = certDir / "vault" / "key.pem"
    val cliCertPem = certDir / "cli" / "cert.pem"
    val cliKeyPem = certDir / "cli" / "key.pem"
    for {
      _ <- IO(os.makeDir.all(certDir))
      _ <- Util.exec(s"$consul tls ca create", cwd = certDir)
      _ <- IO(os.makeDir.all(certDir / "consul"))
      _ <- Util.exec(s"$consul tls cert create -server -additional-dnsname=consul.service.consul", certDir)
      _ <- IO {
        os.move.over(certDir / "dc1-server-consul-0.pem", consulServerCertPem)
        os.move.over(certDir / "dc1-server-consul-0-key.pem", consulServerKeyPem)
      }
      _ <- IO(os.makeDir.all(certDir / "vault"))
      _ <- Util.exec(s"$consul tls cert create -client -additional-dnsname=vault.service.consul", certDir)
      _ <- IO {
        os.move.over(certDir / "dc1-client-consul-0.pem", vaultClientCertPem)
        os.move.over(certDir / "dc1-client-consul-0-key.pem", vaultClientKeyPem)
      }
      _ <- IO(os.makeDir.all(certDir / "cli"))
      _ <- Util.exec(s"$consul tls cert create -cli", certDir)
      _ <- IO {
        os.move.over(certDir / "dc1-cli-consul-0.pem", cliCertPem)
        os.move.over(certDir / "dc1-cli-consul-0-key.pem", cliKeyPem)
        os.makeDir.all(certDir / "ca")
        os.move.over(certDir / "consul-agent-ca.pem", consulCaCertPem)
        os.move.over(certDir / "consul-agent-ca-key.pem", consulCaKeyPem)
      }
    } yield ()
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
