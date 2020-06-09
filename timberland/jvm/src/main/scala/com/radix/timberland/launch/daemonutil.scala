package com.radix.timberland.launch

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.utils.helm.NomadHCL.syntax.JobShim
import com.radix.utils.helm.http4s.Http4sNomadClient
import com.radix.utils.helm.{CatalogListNodesForServiceResponse, NomadOp, NomadReadRaftConfigurationResponse}
import org.http4s.Uri.uri
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import sys.process._
import java.io.{File, PrintWriter}

import com.radix.utils.helm.ConsulOp.CatalogListNodesForService

import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, TimeoutException, duration}
import com.radix.utils.helm.elemental.{ElementalOps, UPNotRetrieved, UPNotSet, UPRetrieved, UPSet}
import java.net.{InetAddress, UnknownHostException}

sealed trait DaemonState
case object AllDaemonsStarted extends DaemonState

case class RegisterProvider(provider: String, client_id: String, client_secret: String)

/** A holder class for combining a task with it's associated tags that need to be all checked for Daemon Availability
  *
  * @param name The full extended task name which is "{jobName}-{groupName}-{taskName}" for any given task
  * @param tagList A list of type Set[String] which are unique combinations of services that should be checked for
  *                availability
  */
case class TaskAndTags(name: String, tagList: List[Set[String]], quorumSize: Int = 1)

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  private def askUser(question: String): IO[String] = IO.delay {
    Console.print(question + " ")
    StdIn.readLine()
  }

  /** Let a specified function run for a specified period of time before interrupting it and raising an error. This
   * function sets up the timeoutTo function.
   *
   * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
   *
   * @param fa    The function to run (This function must return type IO[A])
   * @param after Timeout after this amount of time
   * @param timer A default Timer
   * @param cs    A default ContextShift
   * @tparam A The return type of the function must be IO[A]. A is the type of our result
   * @return Returns the successful completion of the function or a IO.raiseError
   */
  def timeout[A](fa: IO[A], after: FiniteDuration)(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[A] = {

    val error = new TimeoutException(after.toString)
    timeoutTo(fa, after, IO.raiseError(error))
  }

  /** Creates a race condition between two functions (fa and timer.sleep()) that will let a program run until the timer
   * expires
   *
   * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
   *
   * @param fa       The function to race which must return type IO[A]
   * @param after    The duration to let the function run
   * @param fallback The function to run if fa fails
   * @param timer    A default timer
   * @param cs       A default ContextShift
   * @tparam A The type of our result
   * @return Returns the result of fa if it completes within @after or returns fallback (all IO[A])
   */
  def timeoutTo[A](fa: IO[A], after: FiniteDuration, fallback: IO[A])(implicit timer: Timer[IO],
                                                                      cs: ContextShift[IO]): IO[A] = {

    IO.race(fa, timer.sleep(after)).flatMap {
      case Left(a) => IO.pure(a)
      case Right(_) => fallback
    }
  }

  def checkNomadState(implicit interp: Http4sNomadClient[IO]): IO[NomadReadRaftConfigurationResponse] = {
    for {
      state <- NomadOp.nomadReadRaftConfiguration().foldMap(interp)
    } yield state
  }

  /** Wait for a DNS record to become available. Consul will not return a record for failing services.
   * This returns Unit because we do not care what the result is, only that there is at least one.
   *
   * @param dnsName The DNS name to look up
   * @param timeoutDuration How long to wait before throwing an exception
   */
  def waitForDNS(dnsName: String, timeoutDuration: FiniteDuration): IO[Unit] = {
    def queryLoop(): IO[Unit] = for {
      lookupResult <- IO(InetAddress.getAllByName(dnsName)).attempt
      _ <- lookupResult match {
        case Left(_: UnknownHostException) => IO(Console.println(s"[$dnsName] Host not found (yet)")) *> IO.sleep(2.seconds) *> queryLoop
        case Left(err: Throwable) => IO(Console.println(s"[$dnsName] Unexpected result")) *> IO.raiseError(err)
        case Right(addresses: Array[InetAddress]) => addresses match {
          case Array() => IO(Console.println(s"[$dnsName] Successful yet empty DNS response")) *> IO.sleep(2.seconds) *> queryLoop
          case _ => IO(Console.println(s"[$dnsName] Found DNS record(s): ${addresses.map(_.getHostAddress).mkString(", ")}"))
        }
      }
    } yield ()

    timeout(queryLoop, timeoutDuration) *> IO.unit
  }

  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param quorumSize           How many running and valid copies of a Job Group there should be across the specified services
   * @param dev                  Whether to run in dev mode. This is used in conjunction with quorumSize to adjust variables sent to
   *                             the daemon to start it in development mode
   * @param core                 Whether to start core services (Zookeeper, Kafka, Kafka Companions)
   * @param vaultStart           Whether to start Vault
   * @param esStart              Whether to start Elasticsearch
   * @param retoolStart          Whether to start retool
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(integrationTest: Boolean,
                   dev: Boolean,
                   core: Boolean,
                   yugabyteStart: Boolean,
                   vaultStart: Boolean,
                   esStart: Boolean,
                   retoolStart: Boolean,
                   elementalStart: Boolean,
                   upstreamAccessKey: Option[String],
                   upstreamSecretKey: Option[String]): IO[Int] = {
    val execDir = "/opt/radix/timberland/terraform"
    val workingDir = if(integrationTest) new File("/tmp/radix/terraform") else new File("/opt/radix/terraform")
    val mkTmpDirCommand = Seq("bash", "-c", "rm -rf /tmp/radix && mkdir -p /tmp/radix/terraform")
    val initCommand = Seq(s"$execDir/terraform", "init", "-plugin-dir", s"$execDir/plugins", "-from-module", s"$execDir/main")

    val prefix = if(integrationTest) "integration-" else {
      val home = sys.env.getOrElse("HOME", "")
      val file = new File(s"$home/monorepo")
      if(file.exists) Process(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"), Some(new File(s"$home/monorepo"))).!!.trim.toLowerCase.replaceAll("/", "-") + "-" else ""
    }

    val variables: String =
      s"-var='prefix=$prefix' " +
      s"-var='test=$integrationTest' " +
      s"-var='dev=$dev' " +
      s"-var='launch_minio=$core' " +
      s"-var='launch_apprise=$core' " +
      s"-var='launch_zookeeper=$core' " +
      s"-var='launch_kafka=$core' " +
      s"-var='launch_kafka_companions=$core' " +
      s"-var='launch_yugabyte=$yugabyteStart' " +
      s"-var='launch_vault=$vaultStart' " +
      s"-var='launch_es=$esStart' " +
      s"-var='launch_retool=$retoolStart' " +
      s"-var='launch_elemental=$elementalStart' " +
      s"-var='minio_upstream_access_key=${upstreamAccessKey.getOrElse("minio-access-key")}' " +
      s"-var='minio_upstream_secret_key=${upstreamSecretKey.getOrElse("minio-secret-key")}' "

    //TODO don't spawn another bash shell
    val applyCommand = Seq("bash", "-c", s"$execDir/terraform apply -auto-approve " + variables)

    val mkTmpDir = for {
      mkDirExitCode <- IO(Process(mkTmpDirCommand) !)
    } yield mkDirExitCode

    val init = for {
      _ <- IO(Console.println(s"Init command: ${initCommand.mkString(" ")}"))
      initExitCode <- IO(Process(initCommand, Some(workingDir)) !)
    } yield initExitCode

    val apply = for {
      _ <- IO(Console.println(s"Apply command: ${applyCommand.mkString(" ")}"))
      applyExitCode <- IO(Process(applyCommand, Some(workingDir)) !)
    } yield applyExitCode

    if(integrationTest) return mkTmpDir *> init *> apply

    if(workingDir.listFiles.isEmpty)
      init *> apply
    else
      apply
  }


  def waitForQuorum(core: Boolean,
                    yugabyteStart: Boolean,
                    vaultStart: Boolean,
                    esStart: Boolean,
                    retoolStart: Boolean,
                    elementalStart: Boolean): IO[DaemonState] = {
    val coreServices = Vector(
      "zookeeper-daemons-zookeeper-zookeeper",
      "kafka-companion-daemons-kafkaCompanions-kSQL",
      "kafka-companion-daemons-kafkaCompanions-kafkaConnect",
      "kafka-companion-daemons-kafkaCompanions-kafkaRestProxy",
      "kafka-companion-daemons-kafkaCompanions-schemaRegistry",
      "kafka-daemons-kafka-kafka",
      "minio-job-minio-group-nginx-minio",
      "apprise-apprise-apprise",
    )

    val yugabyteServices = Vector(
      "yugabyte-yugabyte-ybmaster",
      "yugabyte-yugabyte-ybtserver",
    )

    //don't include "vault" because it will get tested later when it gets unsealed
    val vaultServices = Vector(
      "vault-daemon-vault-vault",
    )

    val elasticSearchServices = Vector(
      "elasticsearch-elasticsearch-es-generic-node",
      "elasticsearch-kibana-kibana",
    )

    val retoolServices = Vector(
      "retool-retool-postgres",
      "retool-retool-retool-main",
    )

    val elementalServices = Vector(
      "elemental-machines-em-em",
    )

    val allEnabledServices = Vector(
      (coreServices, core),
      (yugabyteServices, yugabyteStart),
      (vaultServices, vaultStart),
      (elasticSearchServices, esStart),
      (retoolServices, retoolStart),
      (elementalServices, elementalStart)
    ).flatMap { case (service, enabled) => if(enabled) Some(service) else None }.flatten

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

    val waitForAllServices = allEnabledServices.parTraverse { service => waitForDNS(s"$service.service.consul", 5.minutes) }

    waitForAllServices *> IO(AllDaemonsStarted)
  }

  def unsealVault(dev: Boolean): IO[Unit] = {
    val starter = new VaultStarter()
    val unseal = for {
      vaultBaseUrl <- starter.lookupVaultBaseUrl()
      vaultUnseal <- starter.unsealVault(dev, vaultBaseUrl)
//      vaultOpen <- consulutil.waitForService("vault", Set("active"), 1)(5.seconds, timer)
      vaultOpen <- waitForDNS("vault.service.consul", 15.seconds)

      oauthId = sys.env.get("GOOGLE_OAUTH_ID")
      oauthSecret = sys.env.get("GOOGLE_OAUTH_SECRET")
      registerGoogleOAuthPlugin <- (vaultUnseal, oauthId, oauthSecret) match {
        case (VaultUnsealed(key: String, token: String), Some(a), Some(b)) =>
          starter.initializeGoogleOauthPlugin(token)
        case (VaultUnsealed(key, token), _, _) =>
          IO(scribe.info(
            "GOOGLE_OAUTH_ID and/or GOOGLE_OAUTH_SECRET are not set. The Google oauth plugin will not be initialized.")) *> IO
            .pure(VaultOauthPluginNotInstalled)
        case (VaultSealed, _, _) =>
          IO(scribe.info(s"Vault remains sealed. Please check your configuration.")) *> IO
            .pure(VaultSealed)
        case (VaultAlreadyUnsealed, _, _) => {
          for {
            _ <- IO("Vault already unsealed")
            unsealToken = sys.env.get("VAULT_TOKEN")
            result <- unsealToken match {
              case Some(token) =>
                starter.initializeGoogleOauthPlugin(token)
              case _ =>
                IO("Vault is already unsealed and VAULT_TOKEN is not set")
            }
          } yield result
        }
      }
      _ <- IO(scribe.info(s"Plugin Status: $registerGoogleOAuthPlugin"))

      _ <- IO(scribe.info(s"VAULT STATUS: ${vaultUnseal}"))
    } yield vaultOpen

    unseal
  }
//  def stopAllServices(): IO[DaemonState] = {
//    BlazeClientBuilder[IO](global).resource.use(implicit client => {
//      scribe.info("Checking Nomad Quorum...")
//
//      implicit val interp: Http4sNomadClient[IO] =
//        new Http4sNomadClient[IO](uri("http://nomad.service.consul:4646"),
//          client)
//      val zk = Zookeeper(1)
//      val kafka = Kafka(1)
//      val kafkaCompanions = KafkaCompanions(1)
//      val es = Elasticsearch(1)
//      val vault = Vault(1)
//
//      for {
////        _ <- zk.stop
////        _ <- kafka.stop
////        _ <- kafkaCompanions.stop
////        _ <- es.stop
////        vaultStatus <- vault.stop
//      } yield vaultStatus
//    })
//  }
}
