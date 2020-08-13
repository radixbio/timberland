package com.radix.timberland.launch

import java.net.NetworkInterface

import ammonite.ops.Path
import cats.effect.{ContextShift, IO, Timer}
import cats.data.NonEmptyList
import cats.implicits._
import com.radix.utils.helm.{HealthStatus}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

package object yugabyte {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  //  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def startYugabyte(master: Boolean, prefix: String)(implicit minQuorumSize: Int): IO[Unit] = {
    for {
      //    _ <- IO.sleep(400.seconds)
      yugabyte_masters <- consulutil
        .waitForService(
          s"${prefix}yugabyte-yugabyte-ybmaster",
          Set("ybmaster", "admin"),
          minQuorumSize,
          fail = false,
          statuses =
            NonEmptyList.of(HealthStatus.Passing, HealthStatus.Critical, HealthStatus.Unknown, HealthStatus.Warning)
        )
        .map(nel =>
          nel
            .map(sr => s"${sr.serviceAddress}:7100")
            .mkString_(",")
        )
      _ <- IO(scribe.debug(s"found yugabyte masters $yugabyte_masters"))
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
        val masterAddressesCmdLine = master match {
          case true  => s"--master_addresses=$yugabyte_masters"
          case false => s"--tserver_master_addrs=$yugabyte_masters"
        }

        val callMethod = master match {
          case true =>
            Seq(
              "/home/yugabyte/bin/yb-master",
              "--fs_data_dirs=/ybmaster_data",
              s"--replication_factor=$minQuorumSize",
              "--enable_ysql=true",
              masterAddressesCmdLine,
              s"--rpc_bind_addresses=$weave"
            )
          case false =>
            Seq(
              "/home/yugabyte/bin/yb-tserver",
              "--fs_data_dirs=/ybtserver_data",
              "--start_pgsql_proxy",
              "--ysql_enable_auth=true",
              "--cql_proxy_bind_address=0.0.0.0:9042",
              "--redis_proxy_bind_address=0.0.0.0:6379",
              masterAddressesCmdLine,
              s"--rpc_bind_addresses=$weave"
            )
          //NOTE FOR LATER: May need to add pgsql bind address
        }
        scribe.debug(s"------Yugabyte call string $callMethod")
        os.proc(callMethod)
          .call(
            stderr = os.Inherit,
            stdout = os.Inherit
          )
      }
    } yield ()
  }
}
