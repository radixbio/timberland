package com.radix.timberland.runtime

import cats.data._
import cats.effect.{ContextShift, Effect, IO}
import cats.implicits._
import java.io.FileWriter
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import ammonite.ops.Path
import com.radix.timberland.radixdefs._
import com.radix.timberland.util._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object Mock {

  import Run._

  class RuntimeNolaunch[F[_]](implicit F: Effect[F]) extends NetworkInfoExec[F] with RuntimeServicesAlg[F] {
    override def searchForPort(netinf: List[String], port: Int): F[Option[NonEmptyList[String]]] = F.liftIO {
      val addrs = for (last <- 0 to 254; octets <- netinf) yield {
        F.liftIO {
          IO.shift(bcs) *> IO {
            Try {
              val s = new Socket()
              s.connect(new InetSocketAddress(octets + last, port), 200)
              s.close()
            } match {
              case Success(_) =>
                scribe.trace(s"able to establish connection to host ${octets + last} on port $port")
                Some(octets + last)
              case Failure(_) =>
                scribe.trace(s"failed to establish connection to host ${octets + last} on port $port")
                None
            }
          } <* IO.shift
        }
      }
      addrs.toList
        .map(F.toIO)
        .parSequence
        .map(_.flatten)
        .map(NonEmptyList.fromList)
        .flatMap(res =>
          IO({
            scribe.debug(
              s"search for port $port on network $netinf has resulted in the following hosts found: ${res.toString}")
            res
          })) <* IO.shift
    }

    override def startConsul(bind_addr: String, consulSeedsO: Option[String], bootstrapExpect: Int): F[Unit] =
      F.delay {
        scribe.debug(s"would have started consul with systemd (bind_addr: $bind_addr)")
      }

    override def startNomad(bind_addr: String, bootstrapExpect: Int, vaultToken: String): F[Unit] =
      F.delay {
        scribe.debug(s"would have started nomad with systemd (bind_addr: $bind_addr) (token: $vaultToken)")
      }

    override def startVault(bind_addr: String): F[Unit] =
      F.delay {
        scribe.debug("would have started vault")
      }

    override def stopConsul(): F[Unit] = F.delay {
      scribe.debug(s"would have stopped consul with systemd")
    }

    override def stopNomad(): F[Unit] = F.delay {
      scribe.debug(s"would have stopped nomad with systemd")
    }

    override def stopVault(): F[Unit] = F.delay {
      scribe.debug(s"would have stopped vault with systemd")
    }

    override def startWeave(hosts: List[String]): F[Unit] =
      F.delay {
        scribe.debug(s"would have launched weave with hosts ${hosts.mkString(" ")}")
      }

  }

}

object Run {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(256))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  val bcs: ContextShift[IO] = IO.contextShift(ec)

  def putStrLn(str: Vector[String]): IO[Unit] =
    if (str.isEmpty) {
      IO.pure(Unit)
    } else {
      putStrLn(str.reduce(_ + "\n" + _))
    }

  def putStrLn(str: String): IO[Unit] = IO(println(str))


  /**
   * This method actually initializes the runtime given a runtime algebra executor.
   * It parses and rewrites default nomad and consul configuration, discovers peers, and
   * actually bootstraps and starts consul and nomad
   *
   * @param consulwd  what's the working directory where we can find the consul configuration and executable binary
   * @param nomadwd   what's the working directory where we can find the nomad configuration and executable binary
   * @param bind_addr are we binding to a specific host IP?
   * @param H         the implementation of the RuntimeServicesAlg to actually give us the ability to start consul and nomad
   * @param F         the effect, F
   * @tparam F the effect type
   * @return a started consul and nomad
   */
  def initializeRuntimeProg[F[_]](consulwd: os.Path, nomadwd: os.Path, bind_addr: Option[String], consulSeedsO: Option[String], bootstrapExpect: Int)(
    implicit H: RuntimeServicesAlg[F],
    F: Effect[F]) = {

    def socks(ifaces: List[String]): F[(Option[cats.data.NonEmptyList[String]], Option[cats.data.NonEmptyList[String]])] = {
      F.liftIO((F.toIO(H.searchForPort(ifaces, 8301)), F.toIO(H.searchForPort(ifaces, 6783))).parMapN {
        case (a, b) => (a, b)
      })
    }

    //Check for dummy network
    for {
      ifaces <- H.getNetworkInterfaces.map(_.filter(x => x.startsWith("169.")))
      dummyStatus <- F.delay {
        ifaces.length match {
          case 0 => {
            scribe.error("Dummy network not running")
            sys.exit(1)
          }
          case _ => scribe.debug("Dummy network running")
        }
      }
    } yield dummyStatus


    for {
      ifaces <- bind_addr match {
        case Some(bind) => F.pure(List(bind.split('.').dropRight(1).mkString(".") + "."))
        case None =>
          H.getNetworkInterfaces.map(
            _.filter(x => x.startsWith("192.") || x.startsWith("10."))
              .map(_.split("\\.").toList.dropRight(1).mkString(".") + "."))
      }
      ipaddrswithcoresrvs <- socks(ifaces)
      weave = ipaddrswithcoresrvs._2
      consul = ipaddrswithcoresrvs._1
//      _ <- F.liftIO(Run.putStrLn(s"weave peers: $weave"))
//      _ <- F.liftIO(Run.putStrLn(s"consul peers: $consul"))

      finalBindAddr <- F.delay {
        bind_addr match {
          case Some(ip) => ip
          case None => {
            val sock = new java.net.DatagramSocket()
            sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
            sock.getLocalAddress.getHostAddress
          }
        }
      }

      consulToken = UUID.randomUUID().toString
      persistentDir = consulwd / os.up
      _ <- F.liftIO(auth.writeTokenConfigs(persistentDir, consulToken))

      consulRestartProc <- H.startConsul(finalBindAddr, consulSeedsO, bootstrapExpect)
      _ <- F.liftIO(auth.setupDefaultConsulToken(persistentDir, consulToken))

      vaultRestartProc <- H.startVault(finalBindAddr)
      vaultSealStatus <- F.liftIO {
        (new VaultStarter).initializeAndUnsealAndSetupVault()
      }
      vaultToken <- F.liftIO( IO((new VaultUtils).findVaultToken()))
      nomadRestartProc <- H.startNomad(finalBindAddr, bootstrapExpect, vaultToken)
      consulNomadToken <- F.liftIO(auth.setupNomadMasterToken(persistentDir, consulToken))

      _ <- F.liftIO(auth.storeMasterTokenInVault(consulNomadToken))
      _ <- F.delay { os.write.over(persistentDir / ".bootstrap-complete", "\n") }

      // _ <- F.liftIO(Run.putStrLn("started consul, nomad, and vault"))
    } yield AuthTokens(consulNomadToken, vaultToken)
  }

  class RuntimeServicesExec[F[_]](implicit F: Effect[F]) extends NetworkInfoExec[F] with RuntimeServicesAlg[F] {
    override def searchForPort(netinf: List[String], port: Int): F[Option[NonEmptyList[String]]] = F.liftIO {
      val addrs = for (last <- 0 to 254; octets <- netinf) yield {
        F.liftIO {
          IO {
            Try {
              val s = new Socket()
              s.connect(new InetSocketAddress(octets + last, port), 200)
              s.close()
            } match {
              case Success(_) => Some(octets + last)
              case Failure(_) => None
            }
          }
        }
      }
      IO.shift(bcs) *> addrs.toList.map(F.toIO).parSequence.map(_.flatten).map(NonEmptyList.fromList) <* IO.shift
    }

    override def startConsul(bind_addr: String, consulSeedsO: Option[String], bootstrapExpect: Int): F[Unit] =
      F.delay {
        val persistentDir = "/opt/radix/timberland"
        LogTUI.event(ConsulStarting)
        LogTUI.writeLog("spawning consul via systemd")

        //TODO enable dev mode in such a way that it doesn't break schema-registry
        val baseArgs = s"-bind=$bind_addr -bootstrap-expect=$bootstrapExpect -config-dir=$persistentDir/consul/config"

        val baseArgsWithSeeds = consulSeedsO match {
          case Some(seedString) =>
            seedString
              .split(',')
              .map { host => s"-retry-join=$host" }
              .foldLeft(baseArgs) { (currentArgs, arg) => currentArgs + ' ' + arg }

          case None => baseArgs
        }

        val envFilePath = Paths.get(s"$persistentDir/consul/consul.env.conf") // TODO make configurable
        val envFileHandle = envFilePath.toFile
        val writer = new FileWriter(envFileHandle)
        writer.write(s"CONSUL_CMD_ARGS=$baseArgsWithSeeds")
        writer.close()

        Thread.sleep(10000)
        os.proc("/usr/bin/sudo", "/bin/systemctl", "restart", "consul").call(stdout = os.Inherit, stderr = os.Inherit)
        LogTUI.event(ConsulSystemdUp)
      }


    override def startNomad(bind_addr: String, bootstrapExpect: Int, vaultToken: String): F[Unit] = {

      F.delay {
        val persistentDir = "/opt/radix/timberland"
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

        os.proc("/usr/bin/sudo", "/bin/systemctl", "restart", "nomad").call(stdout = os.Inherit, stderr = os.Inherit)
        LogTUI.event(NomadSystemdUp)
      }
    }

    def startVault(bind_addr: String): F[Unit] = {
      val persistentDir = "/opt/radix/timberland"
      val args: String =
        s"""VAULT_CMD_ARGS=-address=http://${bind_addr}:8200 -config=$persistentDir/vault/vault_config.conf""".stripMargin

      F.delay {
        LogTUI.event(VaultStarting)
        LogTUI.writeLog("spawning vault via systemd")

        val envFilePath = Paths.get(s"$persistentDir/vault/vault.env.conf")
        val envFileHandle = envFilePath.toFile
        val writer = new FileWriter(envFileHandle)
        writer.write(args)
        writer.close()

        os.proc("/usr/bin/sudo", "/bin/systemctl", "restart", "vault").call(stdout = os.Inherit, stderr = os.Inherit)
        Thread.sleep(10000)
        LogTUI.event(VaultSystemdUp)
      }
    }



    override def stopConsul(): F[Unit] = {
      F.delay{
        scribe.info("Stopping consul via systemd")
        os.proc("/usr/bin/sudo", "/bin/systemctl", "stop", "consul").call(stdout = os.Inherit, stderr = os.Inherit)
      }
    }

    override def stopNomad(): F[Unit] = {
      F.delay{
        scribe.info("Stopping nomad via systemd")
        os.proc("/usr/bin/sudo", "/bin/systemctl", "stop", "nomad").call(stdout = os.Inherit, stderr = os.Inherit)
      }
    }

    override def stopVault(): F[Unit] = {
      F.delay{
        scribe.info("Stopping vault via systemd")
        os.proc("/usr/bin/sudo", "/bin/systemctl", "stop", "vault").call(stdout = os.Inherit, stderr = os.Inherit)
      }
    }

    override def startWeave(hosts: List[String]): F[Unit] = F.delay {
      os.proc("/usr/bin/docker", "plugin", "disable", "weaveworks/net-plugin:latest_release").call(check = false, cwd = os.pwd, stdout = os.Inherit, stderr = os.Inherit)
      os.proc("/usr/bin/docker", "plugin", "set", "weaveworks/net-plugin:latest_release", "IPALLOC_RANGE=10.32.0.0/12").call(check = false, stdout = os.Inherit, stderr = os.Inherit)
      os.proc("/usr/bin/docker", "plugin", "enable", "weaveworks/net-plugin:latest_release").call(stdout = os.Inherit, stderr = os.Inherit)
      //      os.proc(s"/usr/local/bin/weave", "launch", hosts.mkString(" "), "--ipalloc-range", "10.48.0.0/12")
      //        .call(cwd = pwd, check = false, stdout = os.Inherit, stderr = os.Inherit)
      //      os.proc(s"/usr/local/bin/weave", "connect", hosts.mkString(" "))
      //        .call(check = false, stdout = os.Inherit, stderr = os.Inherit)
      ()
    }

  }

  def stopRuntimeProg[F[_]]()(implicit H: RuntimeServicesAlg[F], F: Effect[F]) = {
    for {
      stopConsulProc <- H.stopConsul()
      stopNomadProc <- H.stopNomad()
      stopVaultProc <- H.stopVault()
//      _ <- F.liftIO(Run.putStrLn("stopped consul and nomad"))
    } yield (stopConsulProc, stopNomadProc)
  }

}
