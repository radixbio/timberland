package com.radix.timberland.launch

import java.util.concurrent.Executors

import cats.data.OptionT
import cats.effect.{ContextShift, IO, Timer}
import cats.data.{NonEmptyList, NonEmptyMap}
import cats._
import cats.implicits._
import com.radix.utils.helm
import com.radix.utils.helm.CatalogListNodesForServiceResponse
import com.radix.utils.helm.{ConsulOp, HealthStatus}
import com.radix.utils.helm.http4s.Http4sConsulClient
import org.http4s.Uri.uri
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object consulutil {
  private[this] implicit val timer: Timer[IO]     = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def waitForService(serviceName: String, tags: Set[String], quorum: Int)(
      implicit poll_interval: FiniteDuration = 1.second,
      timer: Timer[IO]): IO[NonEmptyList[CatalogListNodesForServiceResponse]] = {
    BlazeClientBuilder[IO](global).resource.use(client => {
      val interpreter = new Http4sConsulClient[IO](uri("http://consul.service.consul:8500"), client)
      for {
        servicespossiblyempty <- helm.run(interpreter, ConsulOp.catalogListNodesForService(serviceName))
        services              <- IO.pure(NonEmptyList.fromList(servicespossiblyempty))
        _ <- IO(scribe.debug(s"found services $services"))
        matchedAndHealthyNodes <- {
          val srvs = for {
            existingServices <- OptionT.pure[IO](services)
            serviceswithTags <- OptionT.pure[IO](
              existingServices
                .map(_.toList.filter(srv => tags.map(tag => srv.serviceTags.contains(tag)).reduce(_ && _)))
                .flatMap(NonEmptyList.fromList))
            _ <- OptionT.liftF(IO{scribe.debug(s"found nodes that matched tags $tags, $serviceswithTags")})
            healthQuery <- OptionT.pure[IO](serviceswithTags.map(_.map(resp => {
              // Check that all the found ZK's are healthy
              ConsulOp
                .healthListChecksForService(resp.serviceName, Some(resp.datacenter), None, None, None, None)
            }).sequence))
            healthy <- OptionT
              .fromOption[IO](healthQuery.map(helm.run(interpreter, _)))
              .flatMap(OptionT.liftF(_))
              .map(x =>
                x.toList
                  .zip(serviceswithTags.map(_.toList))
                  .filter(_._1.value.map(_.status == HealthStatus.Passing).reduce(_ && _)))
              .map(_.flatMap(_._2))
              .map(NonEmptyList.fromList)
              .flatMap(OptionT.fromOption[IO](_))
          } yield healthy
          srvs.value
        }
        quorumDescision <- matchedAndHealthyNodes match {
          case Some(nel) =>
            if (nel.size < quorum) {
              IO{scribe.debug(s"quorum size of $quorum not found, ${nel.size} out of $quorum so far.")} *> IO.sleep(poll_interval) *> waitForService(serviceName, tags, quorum)
            } else IO.pure(nel)
          case None => IO{scribe.debug(s"no nodes for $serviceName with tags $tags found")} *> IO.sleep(poll_interval) *> waitForService(serviceName, tags, quorum)
        }
      } yield quorumDescision
    })
  }
  val getZKs: OptionT[IO, String] = {
      OptionT.liftF(waitForService("radix-daemons-zookeeper-zookeeper", Set("zookeeper-client", "zookeeper-quorum"), 3)
        .map(nel => nel.map(sr => s"${sr.serviceAddress}:${sr.servicePort}").mkString_(",")))
  }

}
