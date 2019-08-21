package com.radix.timberland.runtime

import cats._
import cats.data._
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

    override def startConsul(consulwd: Path, bind_addr: String, conf: Path, server: Boolean): F[os.SubProcess] =
      F.delay {
        val proc = List(
          "/usr/bin/sudo",
          consulwd.wrapped.toAbsolutePath.toString + "/src/main/resources/consul",
          "agent",
          if (server) { "-server" } else { "" },
          s"""-config-file=${conf.wrapped.toAbsolutePath}""",
          s"-bind=$bind_addr"
        )
        scribe.debug(s"would have started consul with $proc")
        os.proc("/bin/echo", s""" "consul launched with server: $server, conf: $conf, bind_addr: $bind_addr"  """)
          .spawn(consulwd, stdout = os.Inherit, stderr = os.Inherit)
      }
    override def startNomad(nomadwd: Path, bind_addr: String, conf: Path, server: Boolean): F[os.SubProcess] = F.delay {
      val proc = List(
        "/usr/bin/sudo",
        nomadwd.wrapped.toAbsolutePath.toString + "/nomad",
        "agent",
        if (server) { "-server" } else { "" },
        s"""-config=${conf.wrapped.toAbsolutePath}""",
        s"""-bind=$bind_addr"""
      )
      scribe.debug(s"would have started nomad with $proc")
      os.proc("/bin/echo", s""""nomad launched with ${conf.toString()}"""")
        .spawn(nomadwd, stdout = os.Inherit, stderr = os.Inherit)
    }
    override def startWeave(hosts: List[String]): F[Unit] = F.delay {
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

    override def startConsul(consulwd: Path, bind_addr: String, conf: Path, server: Boolean): F[os.SubProcess] =
      F.delay {
        scribe.info("spawning consul")
        val proc = os
          .proc(
            "/usr/bin/sudo",
            consulwd.wrapped.toAbsolutePath.toString + "/consul",
            "agent",
            if (server) { "-server" } else { "" },
            s"""-config-file=${conf.wrapped.toAbsolutePath}""",
            s"-bind=$bind_addr"
          )
          .spawn(consulwd, stdout = os.Inherit, stderr = os.Inherit)
        scribe.info(s"done spawning consul")
        proc
      }
    override def startNomad(nomadwd: Path, bind_addr: String, conf: Path, server: Boolean): F[os.SubProcess] = F.delay {
      scribe.info("spawning nomad")
      val proc = os
        .proc(
          "/usr/bin/sudo",
          nomadwd.wrapped.toAbsolutePath.toString + "/nomad",
          "agent",
          if (server) { "-server" } else { "-client" },
          s"""-config=${conf.wrapped.toAbsolutePath}""",
          s"-bind=$bind_addr"
        )
        .spawn(nomadwd, stdout = os.Inherit, stderr = os.Inherit)
      scribe.info("done spawning nomad")
      proc
    }
    override def startWeave(hosts: List[String]): F[Unit] = F.delay {
      os.proc(s"/usr/local/bin/weave", "launch", hosts.mkString(" "))
        .call(cwd = pwd, check = false, stdout = os.Inherit, stderr = os.Inherit)
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
    * @param bind_ip are we binding to a specific host IP?
    * @param H the implementation of the RuntimeServicesAlg to actually give us the ability to start consul and nomad
    * @param F the effect, F
    * @tparam F the effect type
    * @return a started consul and nomad
    */
  def initializeRuntimeProg[F[_]](consulwd: Path, nomadwd: Path, bind_ip: Option[String])(
      implicit H: RuntimeServicesAlg[F],
      F: Effect[F]) = {
    def socks(ifaces: List[String]) = {
      F.liftIO((F.toIO(H.searchForPort(ifaces, 8301)), F.toIO(H.searchForPort(ifaces, 6783))).parMapN {
        case (a, b) => (a, b)
      })
    }
    for {
      ifaces <- bind_ip match {
        case Some(bind) => F.pure(List(bind.split('.').dropRight(1).mkString(".") + "."))
        case None =>
          H.getNetworkInterfaces.map(
            _.filter(x => x.startsWith("192.") || x.startsWith("10."))
              .map(_.split("\\.").toList.dropRight(1).mkString(".") + "."))
      }
      ipaddrswithcoresrvs <- socks(ifaces)
      weave  = ipaddrswithcoresrvs._2
      consul = ipaddrswithcoresrvs._1
      _         <- F.liftIO(Run.putStrLn(s"weave peers: $weave"))
      _         <- F.liftIO(Run.putStrLn(s"consul peers: $consul"))
      consulcfg <- H.readConfig(consulwd, fname = "consul.json")
      nomadcfg  <- H.readConfig(nomadwd, fname = "nomad.hcl")
      consulcfg <- H.parseJson(consulcfg)
      bind_addr <- F.delay {
        bind_ip match {
          case Some(ip) => ip
          case None => {
            val sock = new java.net.DatagramSocket()
            sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
            sock.getLocalAddress.getHostAddress
          }
        }
      }
      consulSubprocess <- {
        val cfg: OptionT[F, Path] = for {
          cnsul <- OptionT(F.pure(consul))
          conf <- OptionT(
            F.delay(
              consulcfg.hcursor
                .downField("server")
                .set(Json.fromBoolean(cnsul.length <= 5))
                .up
                .downField("retry_join")
                .set(Json.fromValues(cnsul.map(Json.fromString).toList))
                .up
                .downField("client_addr")
                .set(Json.fromString(bind_addr))
                .top))
          _    <- OptionT.liftF(F.liftIO(Run.putStrLn(s"consul starting with config ${conf.toString}")))
          path <- OptionT.liftF(H.mkTempFile(conf.toString, "consul"))

        } yield path
        val res: F[os.SubProcess] = cfg.value
          .map({
            case Some(io) => F.pure(io)
            case None => {
              val cfg = consulcfg.hcursor
                .downField("server")
                .set(Json.fromBoolean(true))
                .up
                .downField("client_addr")
                .set(Json.fromString(bind_addr))
                .top
                .get
                .toString
              for {
                conf <- H.mkTempFile(cfg, "consul")
                _    <- F.liftIO(Run.putStrLn(s"consul starting with config $cfg"))
              } yield conf
            }
          })
          .flatten
          .flatMap(conf =>
            for {
              _ <- F.liftIO(Run.putStrLn(s"consul starting on $bind_addr"))
              c <- H.startConsul(consulwd, conf = conf, bind_addr = bind_addr, server = {
                consul match {
                  case None => true
                  case Some(nel) =>
                    nel.length match {
                      case t if t <= 5 => true
                      case _         => false
                    }
                }
              })
            } yield c
          )
        res
      }
      _         <- F.liftIO(Run.putStrLn("consul up"))
      _         <- H.startWeave(weave.map(_.toList).getOrElse(List.empty[String]))
      _         <- F.liftIO(Run.putStrLn("weave up"))
      nomadconf <- H.mkTempFile(nomadcfg.toString +
                                s"""
                                  |    network_interface = "${NetworkInterface.getByInetAddress(InetAddress.getByName(bind_addr)).getName}"
                                  |}
                                  |consul {
                                  | address = "$bind_addr:8500"
                                  |}
                                  |
                                  |bind_addr = "$bind_addr"
                                """.stripMargin, "nomad", "hcl")
      nomadSubprocess         <- H.startNomad(nomadwd, conf = nomadconf, bind_addr = bind_addr, server = true)
      _         <- F.liftIO(Run.putStrLn("nomad up"))
    } yield (consulSubprocess, nomadSubprocess)
  }

}
