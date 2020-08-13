package com.radix.timberland.launch

import java.net.NetworkInterface

import ammonite.ops.Path
import cats.effect.{ContextShift, IO, Timer}
import cats.data.NonEmptyList
import cats.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

package object kafka {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  //  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def startKafka(prefix: String)(implicit minQuorumSize: Int): IO[Unit] = {
    for {
      zk_connect_env <- consulutil
        .waitForService(
          s"${prefix}zookeeper-daemons-zookeeper-zookeeper",
          Set("zookeeper-client", "zookeeper-quorum"),
          minQuorumSize
        )
        .map(nel => nel.map(sr => s"${sr.serviceAddress}:${sr.servicePort}").mkString_(","))
      _ <- IO(scribe.debug(s"found extant zookeeper servers for kafka $zk_connect_env"))
      _ <- IO {
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
        val interBrokerPort = 29092
        os.proc("/etc/confluent/docker/run")
          .call(
            env = sys.env
              .updated("KAFKA_ZOOKEEPER_CONNECT", zk_connect_env)
              .updated(
                "KAFKA_LISTENERS",
                s"INSIDE://0.0.0.0:$interBrokerPort,OUTSIDE://0.0.0.0:${sys.env("NOMAD_PORT_kafka")}"
              )
              .updated(
                "KAFKA_ADVERTISED_LISTENERS",
                s"INSIDE://$weave:$interBrokerPort,OUTSIDE://${sys.env("NOMAD_ADDR_kafka")}"
              )
              .updated("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT")
              .updated("KAFKA_INTER_BROKER_LISTENER_NAME", "INSIDE"),
            stderr = os.Inherit,
            stdout = os.Inherit
          )
      }
    } yield ()
  }
}
