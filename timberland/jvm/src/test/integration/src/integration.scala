package com.radix.timberland.test.integration

import java.util.ConcurrentModificationException
import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import cats.~>
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.flags.featureFlags.resolveSupersetFlags
import com.radix.timberland.util.VaultUtils
import com.radix.utils.helm.{ConsulOp, HealthStatus, NomadOp}
import com.radix.utils.helm.http4s.{Http4sConsulClient, Http4sNomadClient}
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process.Process

/**
 * An abstract class that should act as the parent class for any integration tests. Note that any subclasses should
 * {@code override lazy val featureFlags = Map(...)} in order to bring up the appropriate services with Terraform.
 */
abstract class TimberlandIntegration extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  val ConsulPort = 8500
  val NomadPort = 4646
  implicit val tokens: AuthTokens = getTokens()

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
    "retool" -> false,
    "elemental" -> false,
    "elasticsearch" -> false
  )

  private lazy val resolvedFlags = resolveSupersetFlags(featureFlags)

  /**
   * Get the master token for Consul/Nomad by querying Vault. This is needed for consulR and nomadR to communicate with
   * the Consul and Nomad HTTP APIs.
   *
   * @return An object containing the vault token and master ACL token for Consul/Nomad.
   */
  def getTokens(): AuthTokens = {
    val vaultToken = new VaultUtils().findVaultToken()
    val getCommand =
      "/opt/radix/timberland/vault/vault kv get -address=http://vault.service.consul:8200 secret/consul-ui-token".split(
        " "
      )
    val consulNomadTokenProc = Process(getCommand, None, "VAULT_TOKEN" -> vaultToken)
    val consulNomadToken = consulNomadTokenProc.lineStream.find(_.contains("token")) match {
      case Some(line) => line.split("\\s+")(1)
      case None       => ""
    }
    AuthTokens(consulNomadToken, vaultToken)
  }

  /**
   * Stop and purge all jobs currently in Nomad with the given prefix using the Nomad HTTP API.
   *
   * @param prefix Any jobs with this prefix will be purged.
   */
  def purgeNomadJobs(prefix: String = "") = {
    val prog = NomadOp.nomadListJobs(prefix).map(_.map(_.name).map(NomadOp.nomadStopJob(_, true)))

    def recur(f: NomadOp ~> IO): IO[Unit] = {
      for {
        left <- prog.foldMap(f).map(_.map(_.foldMap(f)).parSequence).flatten.map(_.toSet)
        _ = println(left)
        res <- if (left.isEmpty) IO.unit else IO.sleep(1.second) *> recur(f)
      } yield res
    }

    nomadR.use(recur).unsafeRunSync()
  }

  val nomadR: Resource[IO, NomadOp ~> IO] = BlazeClientBuilder[IO](implicitly[ExecutionContext]).resource
    .map(client =>
      new Http4sNomadClient[IO](
        baseUri = Uri.unsafeFromString("http://nomad.service.consul:4646"),
        client = client,
        accessToken = Some(tokens.consulNomadToken)
      )
    )

  override def beforeAll(): Unit = {
    super.beforeAll()
    //NOTE: change this None to Some(scribe.LogLevel.Debug) if you want more info as to why your test is failing
    scribe.Logger.root.clearHandlers().clearModifiers().withHandler(minimumLevel = None).replace()

    // Make sure Consul and Nomad are up before using terraform
    val res = daemonutil.waitForDNS("consul.service.consul", 1.minutes) *>
      daemonutil.waitForDNS("nomad.service.consul", 1.minutes) *>
      daemonutil.runTerraform(resolvedFlags, integrationTest = true, None) *> IO(println(resolvedFlags)) *>
      daemonutil.waitForQuorum(resolvedFlags)
    res.unsafeRunSync()
  }

  override def afterAll(): Unit = {
    try {
      super.afterAll()
    } catch {
      case ex: ConcurrentModificationException => ()
    } finally {
      purgeNomadJobs("integration")
    }
  }

  val consulR: Resource[IO, ConsulOp ~> IO] = BlazeClientBuilder[IO](implicitly[ExecutionContext]).resource
    .map(client =>
      new Http4sConsulClient[IO](
        baseUri = Uri.unsafeFromString("http://consul.service.consul:8500"),
        client = client,
        accessToken = Some(tokens.consulNomadToken)
      )
    )

  /**
   * Checks if the service is registered in consul and all its health checks are passing, so that we can use its DNS
   * resolution to find the service.
   *
   * @param svcname The name of the service.
   * @return Whether the service exists and has passing health checks.
   */
  def check(svcname: String): Boolean = {
    consulR
      .use(f =>
        ConsulOp
          .healthListChecksForService(svcname, None, None, None, None, None)
          .foldMap(f)
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
      )
      .unsafeRunSync()
  }

  private val prefix = "integration-"

  "timberland" should "bring up backing runtimesystem containers" in {
    assert(check("nomad"))
    assert(check("nomad-client"))
    assert(check("vault"))
  }

  if (resolvedFlags.getOrElse("apprise", false)) it should "bring up apprise" in {
    assert(check(s"${prefix}apprise-apprise-apprise"))
  }

  if (resolvedFlags.getOrElse("elasticsearch", false)) it should "bring up elasticsearch/kibana" in {
    assert(check(s"${prefix}elasticsearch-elasticsearch-es-generic-node"))
    assert(check(s"${prefix}elasticsearch-kibana-kibana"))
  }

  if (resolvedFlags.getOrElse("elemental", false)) it should "bring up elemental" in {
    assert(check(s"${prefix}elemental-machines-em-em"))
  }

  if (resolvedFlags.getOrElse("kafka", false)) it should "bring up kafka" in {
    assert(check(s"${prefix}kafka-daemons-kafka-kafka"))
  }

  if (resolvedFlags.getOrElse("kafka_companions", false)) it should "bring up kafka companions" in {
    assert(check(s"${prefix}kc-daemons-companions-kSQL"))
    assert(check(s"${prefix}kc-daemons-companions-connect"))
    assert(check(s"${prefix}kc-daemons-companions-rest-proxy"))
    assert(check(s"${prefix}kc-daemons-companions-schema-registry"))
  }

  if (resolvedFlags.getOrElse("minio", false)) it should "bring up minio" in {
    assert(check(s"${prefix}minio-job-minio-group-minio-local"))
    assert(check(s"${prefix}minio-job-minio-group-nginx-minio"))
  }

  if (resolvedFlags.getOrElse("retool", false)) it should "bring up retool/postgres" in {
    assert(check(s"${prefix}retool-retool-postgres"))
    assert(check(s"${prefix}retool-retool-retool-main"))
  }

  if (resolvedFlags.getOrElse("yugabyte", false)) it should "bring up yugabyte" in {
    assert(check(s"${prefix}yugabyte-yugabyte-ybmaster"))
    assert(check(s"${prefix}yugabyte-yugabyte-ybtserver"))
  }

  if (resolvedFlags.getOrElse("zookeeper", false)) it should "bring up zookeeper" in {
    assert(check(s"${prefix}zookeeper-daemons-zookeeper-zookeeper"))
  }

}
