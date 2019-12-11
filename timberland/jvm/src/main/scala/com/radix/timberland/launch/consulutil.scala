package com.radix.timberland.launch

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.utils.helm
import com.radix.utils.helm.http4s.Http4sConsulClient
import com.radix.utils.helm.{CatalogListNodesForServiceResponse, ConsulOp, HealthStatus}
import org.http4s.Uri.uri
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object consulutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def waitForService(serviceName: String, tags: Set[String], quorum: Int, fail: Boolean = false)(
      implicit poll_interval: FiniteDuration = 1.second,
      timer: Timer[IO])
    : IO[List[CatalogListNodesForServiceResponse]] = {
    BlazeClientBuilder[IO](global).resource.use(client => {
      val interpreter =
        new Http4sConsulClient[IO](uri("http://consul.service.consul:8500"),
                                   client)
      for {
        _ <- IO(scribe.debug(s"checking for services that match $serviceName"))
        servicespossiblyempty <- helm
          .run(interpreter, ConsulOp.catalogListNodesForService(serviceName))
        services <- IO.pure(NonEmptyList.fromList(servicespossiblyempty))
        _ <- IO(scribe.debug(s"found services $services"))
        matchedAndHealthyNodes <- {
          val srvs = for {
            existingServices <- OptionT.pure[IO](services)
            serviceswithTags <- OptionT.pure[IO](existingServices
              .map(_.toList.filter(srv =>
                tags.map(tag => srv.serviceTags.contains(tag)).reduce(_ && _)))
              .flatMap(NonEmptyList.fromList))
            _ <- OptionT.liftF(IO {
              scribe.debug(
                s"found nodes that matched tags $tags, $serviceswithTags")
            })
            healthQuery <- OptionT.pure[IO](serviceswithTags.map(_.map(resp => {
              // Check that all the found ZK's are healthy
              ConsulOp
                .healthListChecksForService(resp.serviceName,
                                            Some(resp.datacenter),
                                            None,
                                            None,
                                            None,
                                            None)
            }).sequence))
            healthy <- OptionT
              .fromOption[IO](healthQuery.map(helm.run(interpreter, _)))
              .flatMap(OptionT.liftF(_))
              .map(
                x =>
                  x.toList
                    .zip(serviceswithTags.map(_.toList))
                    .filter(_._1.value
                      .map(_.status == HealthStatus.Passing)
                      .reduce(_ && _)))
              .map(_.flatMap(_._2))
              .map(NonEmptyList.fromList)
              .flatMap(OptionT.fromOption[IO](_))
          } yield healthy
          srvs.value
        }
        quorumDecision <- matchedAndHealthyNodes match {
          case Some(nel) =>
            if (nel.size < quorum) {
              if (fail) {
                IO.pure(List())
              } else {
                IO {
                  scribe.debug(
                    s"quorum size of $quorum not found, ${nel.size} out of $quorum so far.")
                } *> IO.sleep(poll_interval) *> waitForService(serviceName,
                  tags,
                  quorum)
              }
            } else IO.pure(nel.toList)
          case None =>
            if(fail) {
              IO.pure(List())
            } else {
              IO {
                scribe.debug(s"no nodes for $serviceName with tags $tags found")
              } *> IO.sleep(poll_interval) *> waitForService(serviceName,
                tags,
                quorum)
            }
        }
      } yield quorumDecision
    })
  }
  val getZKs: OptionT[IO, String] = {
      OptionT.liftF(waitForService("zookeeper-daemons-zookeeper-zookeeper", Set("zookeeper-client", "zookeeper-quorum"), 3)
        .map(nel => nel.map(sr => s"${sr.serviceAddress}:${sr.servicePort}").mkString_(",")))
  }
  def getDomain(
      implicit service: String,
      tags: List[String],
      client: Client[IO]): OptionT[IO, Int] = //TODO: Generalize this code
    // There's an extra comma due to templating
    OptionT({
      val interpreter =
        new Http4sConsulClient[IO](uri("http://consul.service.consul:8500"),
                                   client)
      for {
        services <- helm.run(interpreter,
                             ConsulOp.catalogListNodesForService(service))
        runningTasks = services
          .filter(
            srv =>
              srv.serviceTags.contains(tags(2)) && srv.serviceTags
                .contains(tags(1)) && srv.serviceName.contains(tags(0)))
          .map(zk => s"${zk.serviceAddress}:${zk.servicePort}")
        _ <- IO(scribe.debug(s"${service}: $runningTasks"))
        res <- if (runningTasks.size > 0) {
          IO.pure(Some(runningTasks.size))
        } else { IO.pure(None) }
      } yield res
    })

//  srv.serviceTags.contains("zookeeper-client") && srv.serviceTags
//    .contains("zookeeper-quorum") && srv.serviceName.contains("radix-daemons"))

  //  val getZKs: OptionT[IO, Int] =
//    OptionT(BlazeClientBuilder[IO](global).resource.use(client => {
//      val interpreter = new Http4sConsulClient[IO](uri("http://consul.service.consul:8500"), client)
//      for {
//        services <- helm.run(interpreter, ConsulOp.catalogListNodesForService("radix-daemons-kafka-zookeeper"))
//        _ <- IO(scribe.debug(s"found services ${services.map(srv => {
//          s"zkclient: ${srv.serviceTags.contains("zookeeper-client")}" +
//            s"quorum: ${srv.serviceTags.contains("zookeeper-quorum")}" +
//            s"radix: ${srv.serviceName.contains("radix-daemons")}" +
//            s"name: ${srv.serviceName}"
//        })}"))
//        zks = services
//          .filter(srv =>
//            srv.serviceTags.contains("zookeeper-client") && srv.serviceTags
//              .contains("zookeeper-quorum") && srv.serviceName.contains("radix-daemons"))
//          .map(zk => s"${zk.serviceAddress}:${zk.servicePort}")
//        _ <- IO(scribe.debug(s"ZKS: $zks"))
//        res <- if (zks.size > 0) {
//          IO.pure(Some(zks.size))
//        } else {
//          IO.pure(None)
//        }
//      } yield res
//    }))
}
