package com.radix.timberland.test.integration

import java.io.File
import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import cats.~>
import com.dimafeng.testcontainers._
import com.radix.timberland.launch.daemonutil
import com.radix.utils.helm.{ConsulOp, HealthStatus, NomadOp}
import com.radix.utils.helm.http4s.{Http4sConsulClient, Http4sNomadClient}
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class TimberlandIntegration extends FlatSpec with Matchers with BeforeAndAfterAll with ForAllTestContainer {

  val ConsulPort = 8500
  val NomadPort = 4646

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(20))

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val T: Timer[IO] = IO.timer(ec)

  val quorumsize = 1
  val dev = true
  val core = true
  val yugabyte = false
  val vault = false
  val retool = false
  val elemental = false
  val elk = false


  val nomadR: Resource[IO, NomadOp ~> IO] = BlazeClientBuilder[IO](ec).resource
    .map(new Http4sNomadClient[IO](baseUri = Uri.unsafeFromString("http://nomad.service.consul:4646"), _))
  override val container = new DockerComposeContainer(new File("timberland/jvm/src/test/integration/resources/docker-compose.yml"))
  override def afterAll(): Unit = {
    val prog = NomadOp.nomadListJobs().map(_.map(_.name).map(NomadOp.nomadStopJob(_, true)))
    def recur(f: NomadOp ~> IO): IO[Unit] =
      for {
        left <- prog.foldMap(f).map(_.map(_.foldMap(f)).parSequence).flatten.map(_.toSet)
        res <- if (left.isEmpty) IO.unit else IO.sleep(1.second) *> recur(f)
      } yield res
    nomadR.use(recur).unsafeRunSync()
    super.afterAll()
  }

  val consulR: Resource[IO, ConsulOp ~> IO] = BlazeClientBuilder[IO](ec).resource
    .map(new Http4sConsulClient[IO](baseUri = Uri.unsafeFromString("http://consul.service.consul:8500"), _))

/**
* Checks if the service is regitered in consul, so that we can use its DNS resolution to find the service.
**/
  def check(svcname: String): Boolean = {
    consulR.use(f => ConsulOp.healthListChecksForService(svcname, None, None, None, None, None).foldMap(f).map(_.value.map(_.status match {
      case HealthStatus.Passing => true
      case _ => false
    }) match {
      case Nil => false
      case head :: Nil => head
      case head :: tl => head && tl.reduce(_ && _)
    })).unsafeRunSync()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    //NOTE: change this None to Some(scribe.LogLevel.Debug) if you want more info as to why your test is failing
    scribe.Logger.root.clearHandlers().clearModifiers().withHandler(minimumLevel = None).replace()
    System.setProperty("test", "true")
    daemonutil.waitForQuorum(quorumsize, dev, core, yugabyte, vault, elk, retool, elemental, 9092, 8001, None, None, None, None).unsafeRunSync()
  }

  "timberland" should "bring up backing runtimesystem containers" in {
    assert(check("nomad"))
    assert(check("nomad-client"))
  }
  if (core) it should "bring up dev services" in {
    assert(check("apprise-apprise-apprise"))
    assert(check("zookeeper-daemons-zookeeper-zookeeper"))
    assert(check("kafka-daemons-kafka-kafka"))
    assert(check("kafka-companion-daemons-kafkaCompanions-kSQL"))
    assert(check("kafka-companion-daemons-kafkaCompanions-kafkaConnect"))
    assert(check("kafka-companion-daemons-kafkaCompanions-kafkaRestProxy"))
    assert(check("kafka-companion-daemons-kafkaCompanions-schemaRegistry"))
    assert(check("minio-job-minio-group-minio-local"))
    assert(check("minio-job-minio-group-nginx-minio"))
    }
  if (retool) it should "bring up retool" in {
    assert(check("retool-retool-postgres"))
    assert(check("retool-retool-retool-main"))
  }
  if (yugabyte) it should "bring up yugabyte" in {
    assert(check("yugabyte-yugabyte-ybmaster"))
    assert(check("yugabyte-yugabyte-ybtserver"))
  }
  if (vault) it should "bring up vault" in {
    assert(check("vault"))
    assert(check("vault-daemon-vault-vault"))
  }
  if (elk) it should "bring up elasticsearch/kibana" in {
    assert(check("elasticsearch-elasticsearch-es-generic-node"))
    assert(check("elasticsearch-kibana-kibana"))
  }



}
