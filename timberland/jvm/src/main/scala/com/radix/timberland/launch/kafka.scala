package com.radix.timberland.launch

import java.net.NetworkInterface

import ammonite.ops.Path
import cats.effect.IO
import cats.data.OptionT
import scala.collection.JavaConverters._

package object kafka {
  val startKafka: IO[Unit] = {
    val res = for {
      // There's an extra comma due to templating
      zk_connect_env <- OptionT(IO(os.read(Path("/local/conf/zoo_servers")).replace("\n", "").dropRight(1).stripMargin match {
        case ""  => None
        case nil if nil.contains("nil") => None
        case els => Some(els)
      }))
      _ <- OptionT.liftF(IO(scribe.debug(s"found extant zookeeper servers for kafka $zk_connect_env")))
      _ <- OptionT.liftF(IO {
        //The weave network is the one to bind to for kafka
        val weave = NetworkInterface.getNetworkInterfaces.asScala.toList
          .map(x => (x.getName, x.getInetAddresses.asScala.toList.find(_.getAddress.length == 4))) // IPV4 only
          .flatMap({
            case (r, Some(v)) => Some((r, v.getHostAddress))
            case (_, None)    => None
          })
          .toMap
          .apply("ethwe0")
        scribe.trace(s"found weave network to bind to $weave")
        os.proc("/etc/confluent/docker/run")
          .spawn(
            env = sys.env
              .updated("KAFKA_ZOOKEEPER_CONNECT", zk_connect_env)
              .updated("KAFKA_ADVERTISED_LISTENERS", s"PLAINTEXT://$weave:${sys.env("NOMAD_PORT_kafka")}"),
            stderr = os.Inherit,
            stdout = os.Inherit
          )
      })
    } yield ()

    res.value.flatMap({
      case Some(r) => IO.pure(r)
      case None    => startKafka
    })
  }
}
