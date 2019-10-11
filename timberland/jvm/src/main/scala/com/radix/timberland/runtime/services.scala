package com.radix.timberland.runtime

import cats._
import cats.data._
import cats.data.NonEmptyList.fromList
import cats.effect.{ContextShift, Effect, IO}
import cats.implicits._
import ammonite._
import ammonite.ops._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import java.net.{InetAddress, InetSocketAddress, NetworkInterface, Socket}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

import com.radix.timberland.radixdefs._

import java.io.{BufferedWriter, File, FileWriter}
import scala.io.Source

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
    override def readConfig(wd: Path, fname: String): F[String] = F.delay {
      scribe.debug(s"trying to load $wd/$fname from file...")
//      Source.fromInputStream(this.getClass.getResourceAsStream(s"$wd/$fname")).getLines.mkString("\n")
      val src = scala.io.Source.fromFile(wd.toIO.toString + "/" + fname)
      val res = src.getLines().mkString("\n")
      src.close()
      res
    }

    override def mkTempFile(contents: String, fname: String, exn: String = "json"): F[Path] = F.liftIO {
      import java.io.{BufferedWriter, File, FileWriter}
      IO.shift(bcs) *> IO {
        val f = File.createTempFile(fname, "." + exn)

        scribe.debug(s"writing tempfile ${f.toPath.toAbsolutePath.toString} with contents:\n $contents")
        val fw = new FileWriter(f)
        val bw = new BufferedWriter(fw)
        bw.write(contents)
        bw.flush()
        fw.flush()
        bw.close()
        fw.close()
        scribe.debug(s"wrote tempfile ${f.toPath.toAbsolutePath.toString}")
        Path(f.toPath)
      }.flatMap(res => IO.shift *> IO.pure(res))
    }

    override def startConsul(bind_addr: String, consulSeedsO: Option[String]): F[Unit] =
      F.delay {
        scribe.debug(s"would have started consul with systemd (bind_addr: $bind_addr)")
      }
    override def startNomad(bind_addr: String): F[Unit] =
      F.delay {
        scribe.debug(s"would have started nomad with systemd (bind_addr: $bind_addr)")
      }
    override def startWeave(hosts: List[String]): F[Unit] =
      F.delay {
        scribe.debug(s"would have launched weave with hosts ${hosts.mkString(" ")}")
      }

    override def parseJson(json: String): F[Json] = F.fromEither {
      parse(json)
    }

  }
}

object Run {
  implicit val ec                             = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(256))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  val bcs: ContextShift[IO]                   = IO.contextShift(ec)
  def putStrLn(str: String): IO[Unit]         = IO(println(str))

  def putStrLn(str: Vector[String]): IO[Unit] =
    if (str.isEmpty) {
      IO.pure(Unit)
    } else {
      putStrLn(str.reduce(_ + "\n" + _))
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
    override def readConfig(wd: Path = pwd, fname: String): F[String] = F.liftIO {
      IO.shift(bcs) *> IO {
        val src = scala.io.Source.fromFile(wd.toIO.toString + "/" + fname)
        val res = src.getLines().mkString("\n")
        src.close()
        res
      } <* IO.shift
    }

    override def mkTempFile(contents: String, fname: String, exn: String = "json"): F[Path] = F.liftIO {
      IO.shift(bcs) *> IO {
        val f  = File.createTempFile(fname, "." + exn)
        val fw = new FileWriter(f)
        val bw = new BufferedWriter(fw)
        bw.write(contents)
        bw.flush()
        fw.flush()
        bw.close()
        fw.close()
        Path(f.toPath)
      } <* IO.shift
    }

    override def startConsul(bind_addr: String, consulSeedsO: Option[String]): F[Unit] =
      F.delay {
        scribe.info("spawning consul via systemd")

        val baseArgs = s"-bind=$bind_addr"
        val baseArgsWithSeeds = consulSeedsO match {
          case Some(seedString) =>
            seedString
              .split(',')
              .map { host => s"-retry-join=$host" }
              .foldLeft(baseArgs){ (currentArgs, arg) => currentArgs + ' ' + arg }

          case None => baseArgs
        }

        os.proc("/usr/bin/sudo", "/bin/systemctl", "set-environment", s"""CONSUL_CMD_ARGS=$baseArgsWithSeeds""").spawn()
        os.proc("/usr/bin/sudo", "/bin/systemctl", "restart", "consul").spawn(null, stdout = os.Inherit, stderr = os.Inherit)
      }

    override def startNomad(bind_addr: String): F[Unit] =
      F.delay {
        scribe.info("spawning nomad via systemd")
        os.proc("/usr/bin/sudo", "/bin/systemctl", "set-environment", s"""NOMAD_CMD_ARGS=-bind=$bind_addr""").spawn()
        os.proc("/usr/bin/sudo", "/bin/systemctl", "restart", "nomad").spawn(null, stdout = os.Inherit, stderr = os.Inherit)
      }

    override def startWeave(hosts: List[String]): F[Unit] = F.delay {
      os.proc("/usr/bin/docker", "plugin", "disable", "weaveworks/net-plugin:latest_release").call(check = false, cwd = pwd, stdout = os.Inherit, stderr = os.Inherit)
      os.proc("/usr/bin/docker", "plugin", "set", "weaveworks/net-plugin:latest_release", "IPALLOC_RANGE=10.48.0.0/12").call(check = false, stdout = os.Inherit, stderr = os.Inherit)
      os.proc("/usr/bin/docker", "plugin", "enable", "weaveworks/net-plugin:latest_release").call(stdout = os.Inherit, stderr = os.Inherit)
//      os.proc(s"/usr/local/bin/weave", "launch", hosts.mkString(" "), "--ipalloc-range", "10.48.0.0/12")
//        .call(cwd = pwd, check = false, stdout = os.Inherit, stderr = os.Inherit)
//      os.proc(s"/usr/local/bin/weave", "connect", hosts.mkString(" "))
//        .call(check = false, stdout = os.Inherit, stderr = os.Inherit)
      ()
    }

    override def parseJson(json: String): F[Json] = F.fromEither {
      parse(json)
    }

  }

  /**
    * This method actually initializes the runtime given a runtime algebra executor.
    * It parses and rewrites default nomad and consul configuration, discovers peers, and
    * actually bootstraps and starts consul and nomad
    * @param consulwd what's the working directory where we can find the consul configuration and executable binary
    * @param nomadwd what's the working directory where we can find the nomad configuration and executable binary
    * @param bind_addr are we binding to a specific host IP?
    * @param H the implementation of the RuntimeServicesAlg to actually give us the ability to start consul and nomad
    * @param F the effect, F
    * @tparam F the effect type
    * @return a started consul and nomad
    */
  def initializeRuntimeProg[F[_]](consulwd: Path, nomadwd: Path, bind_addr: Option[String], consulSeedsO: Option[String])(
      implicit H: RuntimeServicesAlg[F],
      F: Effect[F]) = {

      def socks(ifaces: List[String]): F[(Option[cats.data.NonEmptyList[String]], Option[cats.data.NonEmptyList[String]])] = {
      F.liftIO((F.toIO(H.searchForPort(ifaces, 8301)), F.toIO(H.searchForPort(ifaces,6783))).parMapN {
        case (a,b) => (a,b)
      })
    }



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
      _ <- F.liftIO(Run.putStrLn(s"weave peers: $weave"))
      _ <- F.liftIO(Run.putStrLn(s"consul peers: $consul"))

      final_bind_addr <- F.delay {
        bind_addr match {
          case Some(ip) => ip
          case None => {
            val sock = new java.net.DatagramSocket()
            sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
            sock.getLocalAddress.getHostAddress
          }
        }
      }

      consulRestartProc <- H.startConsul(final_bind_addr, consulSeedsO)
      nomadRestartProc <- H.startNomad(final_bind_addr)
      _ <- F.liftIO(Run.putStrLn("started consul and nomad"))
    } yield (consulRestartProc, nomadRestartProc)
  }
}
