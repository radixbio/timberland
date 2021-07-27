package com.radix.timberland.test.integration

import java.util.ConcurrentModificationException
import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import cats.~>
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.runtime.{auth, AuthTokens}
import com.radix.timberland.flags.featureFlags.resolveSupersetFlags
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.util.{Investigator, Util, VaultUtils}
import com.radix.utils.helm.{ConsulOp, HealthStatus, NomadOp}
import com.radix.utils.helm.http4s.{Http4sConsulClient, Http4sNomadClient}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest._
import com.radix.utils.tls.ConsulVaultSSLContext._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process.Process

/**
 * An abstract class that should act as the parent class for any integration tests. Note that any subclasses should
 * {@code override lazy val featureFlags = Map(...)} in order to bring up the appropriate services with Terraform.
 */
trait TimberlandIntegration extends AsyncFlatSpec with BeforeAndAfterAll {

  val consulPort = 8501
  val nomadPort = 4646
  implicit val tokens: AuthTokens = auth.getAuthTokens(false, ServiceAddrs(), None, None).unsafeRunSync()

  override implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newWorkStealingPool(20))

  implicit val cs: ContextShift[IO] = IO.contextShift(implicitly[ExecutionContext])
  implicit val T: Timer[IO] = IO.timer(implicitly[ExecutionContext])

  val quorumsize = 1

  /**
   * This map represents the services that should be brought up with Terraform. It can be overriden in child classes.
   */
  lazy val featureFlags = Map(
    "dev" -> true,
    "core" -> true,
    "yugabyte" -> false,
    "vault" -> false,
    "elemental" -> false,
    "elasticsearch" -> false
  )

  private lazy val resolvedFlags = resolveSupersetFlags(featureFlags)

  val nomadR: Resource[IO, NomadOp ~> IO] = BlazeClientBuilder[IO](implicitly[ExecutionContext]).resource
    .map(client =>
      new Http4sNomadClient[IO](
        baseUri = Uri.unsafeFromString(s"http://nomad.service.consul:$nomadPort"),
        client = client,
        accessToken = Some(tokens.consulNomadToken)
      )
    )

  override def beforeAll(): Unit = {
    super.beforeAll()
    //NOTE: change this None to Some(scribe.LogLevel.Debug) if you want more info as to why your test is failing
    scribe.Logger.root.clearHandlers().clearModifiers().withHandler(minimumLevel = None).replace()

    // Make sure Consul and Nomad are up before using terraform
    val res = Investigator.waitForService("consul", 1.minutes) *>
      Investigator.waitForService("nomad", 1.minutes) *>
      daemonutil.runTerraform(resolvedFlags, integrationTest = true, None) *> IO(println(resolvedFlags)) *>
      daemonutil.waitForQuorum(resolvedFlags, integrationTest = true)
    res.unsafeRunSync()
  }

  override def afterAll(): Unit = {
    try {
      super.afterAll()
    } catch {
      case ex: ConcurrentModificationException => ()
    } finally {
//      daemonutil.stopTerraform(integrationTest = true)
      // should NOT purge nomad jobs using the nomad http api because terraform gets confused
      // when services it started are stopped by an entity other than terraform
    }
  }

  val interp: ConsulOp ~> IO =
    new Http4sConsulClient[IO](
      Uri.unsafeFromString(s"https://consul.service.consul:${consulPort}"),
      Some(tokens.consulNomadToken)
    )

  /**
   * Checks if the service is registered in consul and all its health checks are passing, so that we can use its DNS
   * resolution to find the service.
   *
   * @param svcname The name of the service.
   * @return Whether the service exists and has passing health checks.
   */
  def check(svcname: String): Boolean = {
    ConsulOp
      .healthListChecksForService(svcname, None, None, None, None, None)
      .foldMap(interp)
      .map(_.value.map(_.status match {
        case HealthStatus.Passing => true
        case x => {
          println("status is " + x);
          false
        }
      }) match {
        case Nil         => false
        case head :: Nil => head
        case head :: tl  => head && tl.reduce(_ && _)
      })
      .unsafeRunSync()
  }

  "timberland" should "bring up backing runtimesystem containers" in {
    assert(check("nomad"))
    assert(check("nomad-client"))
    assert(check("vault"))
  }

  if (resolvedFlags.getOrElse("apprise", false)) it should "bring up apprise" in {
    assert(check(s"apprise"))
    assert(check(s"apprise-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("elasticsearch", false)) it should "bring up elasticsearch/kibana" in {
    assert(check(s"es-rest-0"))
    assert(check(s"es-rest-0-sidecar-proxy"))
    assert(check(s"es-transport-0"))
    assert(check(s"es-transport-0-sidecar-proxy"))
    assert(check(s"kibana"))
    assert(check(s"kibana-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("elemental", false)) it should "bring up elemental" in {
    assert(check(s"elemental-machines-em-em"))
  }

  if (resolvedFlags.getOrElse("kafka", false)) it should "bring up kafka" in {
    assert(check(s"kafka-0"))
    assert(check(s"kafka-0-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("kafka_companions", false)) it should "bring up kafka companions" in {
    assert(check("kc-ksql-service-0"))
    assert(check("kc-ksql-service-0-sidecar-proxy"))
    assert(check("kc-schema-registry-service-0"))
    assert(check("kc-schema-registry-service-0-sidecar-proxy"))
    assert(check("kc-rest-proxy-service-0"))
    assert(check("kc-rest-proxy-service-0-sidecar-proxy"))
    assert(check("kc-connect-service-0"))
    assert(check("kc-connect-service-0-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("minio", false)) it should "bring up minio" in {
    assert(check(s"minio-local-service"))
    assert(check(s"minio-local-service-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("yugabyte", false)) it should "bring up yugabyte" in {
    assert(check("yb-master-head-node-rpc"))
    assert(check("yb-master-head-node-rpc-sidecar-proxy"))
    assert(check("yb-masters-rpc-0"))
    assert(check("yb-masters-rpc-0-sidecar-proxy"))
    assert(check("yb-tserver-connect-0"))
    assert(check("yb-tserver-connect-0-sidecar-proxy"))
  }

  if (resolvedFlags.getOrElse("zookeeper", false)) it should "bring up zookeeper" in {
    assert(check("zookeeper-client-0"))
    assert(check("zookeeper-client-0-sidecar-proxy"))
//    assert(check("zookeeper-follower-0"))
    assert(check("zookeeper-follower-0-sidecar-proxy"))
//    assert(check("zookeeper-othersrvs-0"))
    assert(check("zookeeper-othersrvs-0-sidecar-proxy"))
  }

}
