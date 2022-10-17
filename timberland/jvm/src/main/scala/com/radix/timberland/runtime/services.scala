package com.radix.timberland.runtime

import cats.data._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import java.io.FileWriter
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import com.radix.timberland.util._
import com.radix.utils.tls.ConsulVaultSSLContext

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

object Services {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(256))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val timer: Timer[IO] = IO.timer(global)
  val bcs: ContextShift[IO] = IO.contextShift(ec)

  /**
   * This method actually initializes the runtime given a runtime algebra executor.
   * It parses and rewrites default nomad and consul configuration, discovers peers, and
   * actually bootstraps and starts consul and nomad
   *
   * @param consulwd  what's the working directory where we can find the consul configuration and executable binary
   * @param nomadwd   what's the working directory where we can find the nomad configuration and executable binary
   * @param bind_addr are we binding to a specific host IP?
   * @return a started consul and nomad
   */
  def startServices(
    consulwd: os.Path,
    nomadwd: os.Path,
    bind_addr: Option[String],
    consulSeedsO: Option[String],
    bootstrapExpect: Int,
    setupACL: Boolean
  ): IO[AuthTokens] =
    for {
      finalBindAddr <- IO {
        bind_addr match {
          case Some(ip) => ip
          case None => {
            val sock = new java.net.DatagramSocket()
            sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
            sock.getLocalAddress.getHostAddress
          }
        }
      }
      certDir = os.root / "opt" / "radix" / "certs"
      consulCaCertPemBak = certDir / "ca" / "cert.pem.bak"
      intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
      hasPartiallyBootstrapped <- IO(os.exists(intermediateAclTokenFile))
      consulToken <- makeOrGetIntermediateToken
      persistentDir = consulwd / os.up
      _ <- if (setupACL) auth.writeTokenConfigs(persistentDir, consulToken) else IO.unit
      _ <- IO {
        if (os.exists(consulCaCertPemBak)) {
          os.remove(consulCaCertPemBak)
        }
      }
      _ <- startConsul(finalBindAddr, consulSeedsO, bootstrapExpect)
      _ <- if (setupACL) auth.setupDefaultConsulToken(persistentDir, consulToken) else IO.unit
      _ <- startVault(finalBindAddr)
      _ <- (new VaultStarter).initializeAndUnsealAndSetupVault(setupACL)
      vaultToken <- IO((new VaultUtils).findVaultToken())
      _ <- Util.waitForSystemdString("consul", "Synced service: service=vault:", 30.seconds)
      _ <- startConsulTemplate(consulToken, vaultToken) // wait for consulCaCertPemBak
      _ <- Util.waitForPathToExist(consulCaCertPemBak, 30.seconds)
      _ <- refreshConsulAndVault()
      _ <- startNomad(finalBindAddr, bootstrapExpect, vaultToken)
      consulNomadToken <- if (setupACL) {
        auth.setupNomadMasterToken(persistentDir, consulToken)
      } else {
        auth.getMasterToken
      }
      _ <- auth.storeMasterToken(consulNomadToken)
      _ <- IO(os.write.over(persistentDir / ".bootstrap-complete", "\n"))
      _ <- IO(os.remove(intermediateAclTokenFile))
      // If there are bad certs, nomad also needs 10 seconds to start working properly
//      _ <- if (!setupACL || hasPartiallyBootstrapped) IO.sleep(10.seconds) else IO.unit
      _ <- IO(scribe.info("started consul, nomad, and vault"))
    } yield AuthTokens(consulNomadToken, vaultToken)

  private def refreshConsulAndVault()(implicit timer: Timer[IO]) =
    for {
      consulPidRes <- IO(
        Util
          .proc("/bin/systemctl", "show", "-p", "MainPID", "consul")
          .call(stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
      )
      consulPid = consulPidRes.out.lines().mkString.split("=").last
      vaultPidRes <- IO(
        Util
          .proc("/bin/systemctl", "show", "-p", "MainPID", "vault")
          .call(stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
      )
      vaultPid = vaultPidRes.out.lines().mkString.split("=").last
      _ <- IO(
        Util
          .proc("kill", "-HUP", consulPid, vaultPid)
          .call(
            stderr = os.ProcessOutput(LogTUI.writeLogFromStream),
            stdout = os.ProcessOutput(LogTUI.writeLogFromStream)
          )
      )
      _ <- IO.sleep(5.seconds)
      _ = ConsulVaultSSLContext.refreshCerts()
    } yield ()

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

  def startConsul(bind_addr: String, consulSeedsO: Option[String], bootstrapExpect: Int): IO[Unit] = {
    val persistentDir = RadPath.runtime / "timberland"
    //TODO enable dev mode in such a way that it doesn't break schema-registry
    val baseArgs = s"-bind=$bind_addr -bootstrap-expect=$bootstrapExpect -config-dir=$persistentDir/consul/config"

    val baseArgsWithSeeds = consulSeedsO match {
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

    val envFilePath = Paths.get(s"$persistentDir/consul/consul.env.conf") // TODO make configurable
    val envFileHandle = envFilePath.toFile
    val writer = new FileWriter(envFileHandle)
    writer.write(s"CONSUL_CMD_ARGS=$baseArgsWithSeeds")
    writer.close()
    val consulConfigDir = RadPath.runtime / "timberland" / "consul" / "config"
    for {
      _ <- IO(LogTUI.event(ConsulStarting))
      _ <- IO(os.copy.over(consulConfigDir / "consul-no-tls.json", consulConfigDir / "consul.json"))
      _ <- Util.exec("systemctl restart consul")
      _ <- IO(Util.waitForPortUp(8500, 10.seconds))
      _ <- IO(Util.waitForDNS("consul.service.consul", 30.seconds))
      _ <- IO(makeTempCerts(persistentDir))
      _ <- IO(os.copy.over(consulConfigDir / "consul-tls.json", consulConfigDir / "consul.json"))
      _ <- Util.exec("systemctl restart consul")
      _ <- IO(LogTUI.event(ConsulSystemdUp))
      _ <- IO(Util.waitForPortUp(8501, 10.seconds))
      _ <- IO(Util.waitForDNS("consul.service.consul", 30.seconds))
    } yield IO.unit
  }

  def startConsulTemplate(consulToken: String, vaultToken: String): IO[Unit] = {
    val persistentDir = os.Path("/opt/radix/timberland")
    val envFilePath = persistentDir / "consul-template" / "consul-template.env.conf"
    val envVars =
      s"""CONSUL_TEMPLATE_CMD_ARGS=-config=$persistentDir/consul-template/config.hcl
         |CONSUL_TOKEN=$consulToken
         |VAULT_TOKEN=$vaultToken
         |""".stripMargin
    IO {
      os.write.over(envFilePath, envVars)
      Util
        .proc(
          "/bin/systemctl",
          "restart",
          "consul-template"
        )
        .call(
          stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
          stderr = os.ProcessOutput(LogTUI.writeLogFromStream)
        )
    }
  }

  def startNomad(bind_addr: String, bootstrapExpect: Int, vaultToken: String): IO[Unit] = IO {
    val persistentDir = RadPath.runtime / "timberland"
    val args: String =
      s"""NOMAD_CMD_ARGS=-bind=$bind_addr -bootstrap-expect=$bootstrapExpect -config=$persistentDir/nomad/config
         |VAULT_TOKEN=$vaultToken
         |""".stripMargin
    LogTUI.event(NomadStarting)
    LogTUI.writeLog("spawning nomad via systemd")

    val envFilePath = Paths.get(s"$persistentDir/nomad/nomad.env.conf") // TODO make configurable
    val envFileHandle = envFilePath.toFile
    val writer = new FileWriter(envFileHandle)
    writer.write(args)
    writer.close()

    Util
      .proc("/usr/bin/sudo", "/bin/systemctl", "restart", "nomad")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
    LogTUI.event(NomadSystemdUp)
  }

  def startVault(bind_addr: String): IO[Unit] = {
    val persistentDir = RadPath.runtime / "timberland"
    val args: String =
      s"""VAULT_CMD_ARGS=-address=https://${bind_addr}:8200 -config=$persistentDir/vault/vault_config.conf""".stripMargin

    IO {
      LogTUI.event(VaultStarting)
      LogTUI.writeLog("spawning vault via systemd")

      val envFilePath = Paths.get(s"$persistentDir/vault/vault.env.conf")
      val envFileHandle = envFilePath.toFile
      val writer = new FileWriter(envFileHandle)
      writer.write(args)
      writer.close()

      Util
        .proc("/bin/systemctl", "restart", "vault")
        .call(
          stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
          stderr = os.ProcessOutput(LogTUI.writeLogFromStream)
        )
      for {
        _ <- Util.waitForPortUp(8200, 30.seconds)
      } yield IO.unit
      LogTUI.event(VaultSystemdUp)
    }
  }

  private def makeOrGetIntermediateToken: IO[String] = IO {
    val intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
    if (os.exists(intermediateAclTokenFile)) {
      os.read(intermediateAclTokenFile)
    } else {
      val token = UUID.randomUUID().toString
      os.write(intermediateAclTokenFile, token, os.PermSet(400))
      token
    }
  }

  private def makeTempCerts(persistentDir: os.Path) = {
    val consul = persistentDir / "consul" / "consul"
    val certDir = os.root / "opt" / "radix" / "certs"
    val consulCaCertPem = certDir / "ca" / "cert.pem"
    val consulCaKeyPem = certDir / "ca" / "key.pem"
    os.makeDir.all(certDir)
    Util
      .proc(consul, "tls", "ca", "create")
      .call(
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream),
        cwd = certDir
      )

    val consulServerCertPem = certDir / "consul" / "cert.pem"
    val consulServerKeyPem = certDir / "consul" / "key.pem"
    os.makeDir.all(certDir / "consul")
    Util
      .proc(consul, "tls", "cert", "create", "-server", "-additional-dnsname=consul.service.consul")
      .call(
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream),
        cwd = certDir
      )
    os.move.over(certDir / "dc1-server-consul-0.pem", consulServerCertPem)
    os.move.over(certDir / "dc1-server-consul-0-key.pem", consulServerKeyPem)

    val vaultClientCertPem = certDir / "vault" / "cert.pem"
    val vaultClientKeyPem = certDir / "vault" / "key.pem"
    os.makeDir.all(certDir / "vault")
    Util
      .proc(consul, "tls", "cert", "create", "-client", "-additional-dnsname=vault.service.consul")
      .call(
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream),
        cwd = certDir
      )
    os.move.over(certDir / "dc1-client-consul-0.pem", vaultClientCertPem)
    os.move.over(certDir / "dc1-client-consul-0-key.pem", vaultClientKeyPem)

    val cliCertPem = certDir / "cli" / "cert.pem"
    val cliKeyPem = certDir / "cli" / "key.pem"
    os.makeDir.all(certDir / "cli")
    Util
      .proc(consul, "tls", "cert", "create", "-cli")
      .call(
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream),
        cwd = certDir
      )
    os.move.over(certDir / "dc1-cli-consul-0.pem", cliCertPem)
    os.move.over(certDir / "dc1-cli-consul-0-key.pem", cliKeyPem)

    os.makeDir.all(certDir / "ca")
    os.move.over(certDir / "consul-agent-ca.pem", consulCaCertPem)
    os.move.over(certDir / "consul-agent-ca-key.pem", consulCaKeyPem)
  }

  def stopConsul(): IO[Unit] = IO {
    scribe.info("Stopping consul via systemd")
    Util
      .proc("/usr/bin/sudo", "/bin/systemctl", "stop", "consul")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
  }

  def stopConsulTemplate(): IO[Unit] = IO {
    scribe.info("Stopping consul template via systemd")
    Util
      .proc("/usr/bin/sudo", "/bin/systemctl", "stop", "consul-template")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
  }

  def stopNomad(): IO[Unit] = IO {
    scribe.info("Stopping nomad via systemd")
    Util
      .proc("/usr/bin/sudo", "/bin/systemctl", "stop", "nomad")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
  }

  def stopVault(): IO[Unit] = IO {
    scribe.info("Stopping vault via systemd")
    Util
      .proc("/usr/bin/sudo", "/bin/systemctl", "stop", "vault")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
  }

  def startWeave(hosts: List[String]): IO[Unit] = IO {
    Util
      .proc("/usr/bin/docker", "plugin", "disable", "weaveworks/net-plugin:latest_release")
      .call(
        check = false,
        cwd = os.pwd,
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream)
      )
    Util
      .proc("/usr/bin/docker", "plugin", "set", "weaveworks/net-plugin:latest_release", "IPALLOC_RANGE=10.32.0.0/12")
      .call(
        check = false,
        stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
        stderr = os.ProcessOutput(LogTUI.writeLogFromStream)
      )
    Util
      .proc("/usr/bin/docker", "plugin", "enable", "weaveworks/net-plugin:latest_release")
      .call(stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
    //      proc(s"/usr/local/bin/weave", "launch", hosts.mkString(" "), "--ipalloc-range", "10.48.0.0/12")
    //        .call(cwd = pwd, check = false, stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
    //      proc(s"/usr/local/bin/weave", "connect", hosts.mkString(" "))
    //        .call(check = false, stdout = os.ProcessOutput(LogTUI.writeLogFromStream), stderr = os.ProcessOutput(LogTUI.writeLogFromStream))
    ()
  }

  def stopServices(): IO[Unit] = stopConsul() *> stopConsulTemplate() *> stopNomad() *> stopVault()

}
