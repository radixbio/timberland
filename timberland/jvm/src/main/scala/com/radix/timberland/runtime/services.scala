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
   * @param bindAddr are we binding to a specific host IP?
   * @return a started consul and nomad
   */
  def startServices(
    consulwd: os.Path,
    nomadwd: os.Path,
    bindAddr: Option[String],
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    setupACL: Boolean,
    clientJoin: Boolean
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
      consulCliCertPemBak = certDir / "cli" / "cert.pem.bak"
      intermediateAclTokenFile = RadPath.runtime / "timberland" / ".intermediate-acl-token"
      hasPartiallyBootstrapped <- IO(os.exists(intermediateAclTokenFile))
      consulToken <- makeOrGetIntermediateToken
      persistentDir = consulwd / os.up
      serverJoin = (leaderNodeO.isDefined && !clientJoin)
      remoteJoin = clientJoin | serverJoin
      _ <- if (setupACL) auth.writeTokenConfigs(persistentDir, consulToken) else IO.unit
      _ <- IO {
        if (os.exists(consulCliCertPemBak)) {
          os.remove(consulCliCertPemBak)
        }
      }
      _ <- startConsul(finalBindAddr, leaderNodeO, bootstrapExpect, clientJoin)
      _ <- if (setupACL) auth.setupDefaultConsulToken(persistentDir, consulToken) else IO.unit
      _ <- if (!remoteJoin) for {
        _ <- startVault(finalBindAddr)
        _ <- (new VaultStarter).initializeAndUnsealAndSetupVault(setupACL)
        _ <- Util.waitForSystemdString("consul", "Synced service: service=vault:", 30.seconds)
      } yield ()
      else IO.unit
      vaultToken <- IO((new VaultUtils).findVaultToken())
      _ <- startConsulTemplate(consulToken, vaultToken) // wait for consulCaCertPemBak
      _ <- Util.waitForPathToExist(consulCliCertPemBak, 30.seconds)
      _ <- if (remoteJoin)
        IO(
          os.copy.over(
            RadPath.runtime / "timberland" / "consul" / "consul-client-tls.json",
            RadPath.runtime / "timberland" / "consul" / "config" / "consul.json"
          )
        )
      else IO.unit
      _ <- refreshConsul()
      _ <- if (!remoteJoin) refreshVault() else IO.unit
      _ <- startNomad(finalBindAddr, leaderNodeO, bootstrapExpect, vaultToken, clientJoin)
      consulNomadToken <- if (setupACL & !remoteJoin) {
        auth.setupNomadMasterToken(persistentDir, consulToken)
      } else if (setupACL & remoteJoin)
        IO(consulToken)
      else {
        auth.getMasterToken
      }
      _ <- if (!remoteJoin) auth.storeMasterToken(consulNomadToken) else IO.unit
      _ <- if (serverJoin) { // add -server to consul invocation
        val consulEnvFilePath = RadPath.runtime / "timberland" / "consul" / "consul.env.conf"
        for {
          consulArgStr <- IO(os.read(consulEnvFilePath))
          _ <- IO(os.write.over(consulEnvFilePath, s"$consulArgStr -server"))
          _ <- Util.exec("systemctl restart consul")
        } yield ()
      } else IO.unit
      _ <- IO(os.write.over(persistentDir / ".bootstrap-complete", "\n"))
      _ <- IO(if (os.exists(intermediateAclTokenFile)) os.remove(intermediateAclTokenFile) else ())
      _ <- IO(scribe.info("started services"))
    } yield AuthTokens(consulNomadToken, vaultToken)

  private def refreshConsul()(implicit timer: Timer[IO]) =
    for {
      consulPidRes <- Util.exec("/bin/systemctl show -p MainPID consul")
      consulPid = consulPidRes.stdout.split("=").last.strip
      _ <- Util.exec(s"kill -HUP $consulPid")
      _ <- IO.sleep(5.seconds)
      _ = ConsulVaultSSLContext.refreshCerts()
    } yield ()

  private def refreshVault()(implicit timer: Timer[IO]) =
    for {
      vaultPidRes <- Util.exec("/bin/systemctl show -p MainPID vault")
      vaultPid = vaultPidRes.stdout.split("=").last.strip
      _ <- Util.exec(s"kill -HUP $vaultPid")
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

  def startConsul(
    bindAddr: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    clientJoin: Boolean = false
  ): IO[Unit] = {
    val persistentDir = RadPath.runtime / "timberland"
    val baseArgs = s"-bind=$bindAddr -config-dir=$persistentDir/consul/config"
    val serverJoin = (leaderNodeO.isDefined && !clientJoin)
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
    val consulArgStr = s"CONSUL_CMD_ARGS=$baseArgsWithSeedsAndServer"
    val envFilePath = Paths.get(s"$persistentDir/consul/consul.env.conf") // TODO make configurable
    os.write.over(os.Path(envFilePath), consulArgStr)
    val consulConfigDir = RadPath.runtime / "timberland" / "consul" / "config"
    val consulDir = RadPath.runtime / "timberland" / "consul"
    remoteJoin match {
      case false =>
        for {
          _ <- IO(LogTUI.event(ConsulStarting))
          _ <- IO(os.copy.over(consulDir / "consul-server-bootstrap.json", consulConfigDir / "consul.json"))
          _ <- Util.exec("systemctl restart consul")
          _ <- Util.waitForPortUp(8500, 10.seconds)
          _ <- makeTempCerts(persistentDir)
          _ <- IO(os.copy.over(consulDir / "consul-server.json", consulConfigDir / "consul.json"))
          _ <- Util.exec("systemctl restart consul")
          _ <- IO(LogTUI.event(ConsulSystemdUp))
          _ <- Util.waitForPortUp(8501, 10.seconds)
        } yield ()
      case true =>
        for {
          _ <- IO(LogTUI.event(ConsulStarting))
          _ <- IO(os.copy.over(consulDir / "consul-client.json", consulConfigDir / "consul.json"))
          _ <- Util.exec("systemctl restart consul")
          _ <- Util.waitForPortUp(8500, 10.seconds)
          _ <- Util.waitForPortUp(8501, 10.seconds)
          _ <- makeTempCerts(persistentDir)
          _ <- IO(LogTUI.event(ConsulSystemdUp))
        } yield ()
    }
  }

  def startConsulTemplate(consulToken: String, vaultToken: String): IO[Unit] = {
    val persistentDir = os.Path("/opt/radix/timberland")
    val envFilePath = persistentDir / "consul-template" / "consul-template.env.conf"
    val envVars =
      s"""CONSUL_TEMPLATE_CMD_ARGS=-config=$persistentDir/consul-template/config.hcl
         |CONSUL_TOKEN=$consulToken
         |VAULT_TOKEN=$vaultToken
         |""".stripMargin
    for {
      _ <- IO(os.write.over(envFilePath, envVars))
      _ <- Util.exec("/bin/systemctl restart consul-template")
    } yield ()
  }

//  def startNomad(bindAddr: String, bootstrapExpect: Int, vaultToken: String, remoteJoin: Boolean = false): IO[Unit] = {
  def startNomad(
    bindAddr: String,
    leaderNodeO: Option[String],
    bootstrapExpect: Int,
    vaultToken: String,
    clientJoin: Boolean = false
  ): IO[Unit] = {
    val persistentDir = RadPath.runtime / "timberland"
    val serverJoin = leaderNodeO.isDefined
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
    val args: String =
      s"""NOMAD_CMD_ARGS=$baseArgsWithSeeds -bind=$bindAddr -config=$persistentDir/nomad/config
         |VAULT_TOKEN=$vaultToken
         |""".stripMargin
    for {
      _ <- IO {
        LogTUI.event(NomadStarting)
        LogTUI.writeLog("spawning nomad via systemd")
        os.write.over(persistentDir / "nomad" / "nomad.env.conf", args)
      }
      procOut <- Util.exec("/bin/systemctl restart nomad")
      _ <- IO(LogTUI.event(NomadSystemdUp))
    } yield ()
  }

  def startVault(bindAddr: String): IO[Unit] = {
    val persistentDir = RadPath.runtime / "timberland"
    val args: String =
      s"""VAULT_CMD_ARGS=-address=https://${bindAddr}:8200 -config=$persistentDir/vault/vault_config.conf""".stripMargin
    for {
      _ <- IO {
        LogTUI.event(VaultStarting)
        LogTUI.writeLog("spawning vault via systemd")
        os.write.over(persistentDir / "vault" / "vault.env.conf", args)
      }
      restartProc <- Util.exec("/bin/systemctl restart vault")
      _ <- Util.waitForPortUp(8200, 30.seconds)
      _ <- IO(LogTUI.event(VaultSystemdUp))
    } yield ()

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

  def stopConsul(): IO[Unit] = IO {
    scribe.info("Stopping consul via systemd")
    for {
      stopProc <- Util.exec("/bin/systemctl stop consul")
    } yield stopProc
  }

  def stopConsulTemplate(): IO[Unit] = IO {
    scribe.info("Stopping consul template via systemd")
    for {
      stopProc <- Util.exec("/bin/systemctl stop consul-template")
    } yield stopProc
  }

  def stopNomad(): IO[Unit] = IO {
    scribe.info("Stopping nomad via systemd")
    for {
      stopProc <- Util.exec("/bin/systemctl stop nomad")
    } yield stopProc
  }

  def stopVault(): IO[Unit] = IO {
    scribe.info("Stopping vault via systemd")
    for {
      stopProc <- Util.exec("/bin/systemctl stop vault")
    } yield stopProc
  }

  def startWeave(hosts: List[String]): IO[Unit] =
    for {
      disableProc <- Util.exec("/usr/bin/docker plugin disable weaveworks/net-plugin:latest_release")
      setProc <- Util.exec("/usr/bin/docker plugin set weaveworks/net-plugin:latest_release IPALLOC_RANGE=10.32.0.0/12")
      enableProc <- Util.exec("/usr/bin/docker plugin disable weaveworks/net-plugin:latest_release")
    } yield ()

  def stopServices(): IO[Unit] = stopConsul() *> stopConsulTemplate() *> stopNomad() *> stopVault()

}
