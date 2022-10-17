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
import java.net.UnknownHostException

import com.radix.utils.helm.ConsulOp.CatalogListNodesForService

import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, TimeoutException, duration}
import com.radix.utils.helm.elemental.{ElementalOps, UPNotRetrieved, UPNotSet, UPRetrieved, UPSet}
import org.xbill.DNS
import com.radix.timberland.runtime.flags

sealed trait DaemonState
case object AllDaemonsStarted extends DaemonState

case class RegisterProvider(provider: String, client_id: String, client_secret: String)

case class ServiceAddrs(consulAddr: String = "consul.service.consul",
                        nomadAddr: String = "nomad.service.consul",
                        vaultAddr: String = "vault.service.consul")

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
    val dnsQuery = new DNS.Lookup(dnsName, DNS.Type.SRV, DNS.DClass.IN)

    def queryProg(): IO[Unit] = for {
      _ <- IO(Console.println(s"Waiting for DNS: $dnsName"))
      dnsAnswers <- IO(Option(dnsQuery.run.toSeq).getOrElse(Seq.empty))
      _ <- if(dnsQuery.getResult != DNS.Lookup.SUCCESSFUL || dnsAnswers.isEmpty)
        IO.sleep(1.seconds) *> queryProg
      else
        IO(Console.println(s"Found DNS record: $dnsName"))
    } yield ()

    timeout(queryProg, timeoutDuration) *> IO.unit
  }

  def getServiceIps(remoteConsulDnsAddress: String): IO[ServiceAddrs] = {
    def queryDns(host: String) = IO {
      try {
        DNS.Address.getByName(host).getHostAddress
      } catch {
        case _: UnknownHostException => {
          scribe.error(s"Cannot resolve ip address of $host")
          sys.exit(1)
        }
      }
    }

    for {
      consulAddr <- queryDns("consul.service.consul")
      nomadAddr <- queryDns("nomad.service.consul")
      // vaultAddr <- queryDns("vault.service.consul")
    } yield ServiceAddrs(consulAddr, nomadAddr)
  }

  def getTerraformWorkDir(is_integration: Boolean): File = {
    if(is_integration) new File("/tmp/radix/terraform") else new File("/opt/radix/terraform")
  }
  val execDir = "/opt/radix/timberland/terraform"
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
  def runTerraform(featureFlags: Map[String, Boolean],
                   integrationTest: Boolean,
                   upstreamAccessKey: Option[String],
                   upstreamSecretKey: Option[String],
                   serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val mkTmpDirCommand = Seq("bash", "-c", "rm -rf /tmp/radix && mkdir -p /tmp/radix/terraform")

    val prefix = if(integrationTest) "integration-" else {
      val home = sys.env.getOrElse("HOME", "")
      val file = new File(s"$home/monorepo")
      if(file.exists) Process(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"), Some(new File(s"$home/monorepo"))).!!.trim.toLowerCase.replaceAll("/", "-") + "-" else ""
    }

    val variables =
      s"-var='prefix=$prefix' " +
      s"-var='test=$integrationTest' " +
      s"-var='minio_upstream_access_key=${upstreamAccessKey.getOrElse("minio-access-key")}' " +
      s"-var='minio_upstream_secret_key=${upstreamSecretKey.getOrElse("minio-secret-key")}' " +
      s"-var='consul_address=${serviceAddrs.consulAddr}' " +
      s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
      s"""-var='feature_flags=["${featureFlags.filter(_._2).keys.mkString("""","""")}"]'"""

    //TODO don't spawn another bash shell
    val applyCommand = Seq("bash", "-c", s"$execDir/terraform apply -auto-approve " + variables)

    val mkTmpDir = for {
      mkDirExitCode <- IO(Process(mkTmpDirCommand) !)
    } yield mkDirExitCode

    val apply = for {
      _ <- IO(Console.println(s"Apply command: ${applyCommand.mkString(" ")}"))
      applyExitCode <- IO(Process(applyCommand, Some(workingDir)) !)
    } yield applyExitCode

    if(integrationTest)
      mkTmpDir *> initTerraform(integrationTest, true, serviceAddrs) *> apply
    else
      initTerraform(integrationTest, true, serviceAddrs) *> apply
  }

  def initTerraform(integrationTest: Boolean,
                    connectToBackend: Boolean,
                    serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Unit] = {
    val initAgainCommand = Seq(
      s"$execDir/terraform", "init",
      "-plugin-dir", s"$execDir/plugins",
      s"-backend=$connectToBackend",
      s"-backend-config=address=${serviceAddrs.consulAddr}:8500"
    )
    val initFirstCommand = initAgainCommand ++ Seq("-from-module", s"$execDir/main")
    val workingDir = getTerraformWorkDir(integrationTest)

    for {
      alreadyInitialized <- IO(workingDir.listFiles.nonEmpty)
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- if (connectToBackend) IO(Console.println(s"Init command: ${initCommand.mkString(" ")}")) else IO.unit
      _ <- IO(os.proc(initCommand).call(cwd = os.Path(workingDir), check = false))
    } yield ()
  }

  def stopTerraform(integrationTest: Boolean): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val cmd = Seq("bash", "-c", s"$execDir/terraform destroy -auto-approve")
    println(s"Destroy command ${cmd.mkString(" ")}")
    val ret = os.proc(cmd).call(stdout = os.Inherit, stderr = os.Inherit, cwd = os.Path(workingDir))
    IO(ret.exitCode)
  }

  def waitForQuorum(featureFlags: Map[String, Boolean]): IO[DaemonState] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

    val enabledServices = featureFlags.toList.flatMap {
      case (feature, enabled) => if (enabled) flags.flagServiceMap.getOrElse(feature, Vector()) else Vector()
    }

    enabledServices.parTraverse { service =>
      waitForDNS(s"$service.service.consul", 5.minutes)
    } *> IO.pure(AllDaemonsStarted)
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
