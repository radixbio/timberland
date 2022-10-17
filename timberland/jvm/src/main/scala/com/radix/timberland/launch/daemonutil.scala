package com.radix.timberland.launch

import java.io.{File, IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, UnknownHostException}

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.radix.timberland.util.VaultUtils
import com.radix.utils.helm.http4s.Http4sNomadClient
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.{NomadOp, NomadReadRaftConfigurationResponse}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._

import sys.process._
import com.radix.timberland.radixdefs.ServiceAddrs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, TimeoutException}
import scala.concurrent.duration._
import org.xbill.DNS
import com.radix.timberland.runtime.flags
import org.http4s.Uri
import org.http4s.client.Client

import scala.io.StdIn

sealed trait DaemonState

case object AllDaemonsStarted extends DaemonState

/** A holder class for combining a task with it's associated tags that need to be all checked for Daemon Availability
 *
 * @param name    The full extended task name which is "{jobName}-{groupName}-{taskName}" for any given task
 * @param tagList A list of type Set[String] which are unique combinations of services that should be checked for
 *                availability
 */
case class TaskAndTags(name: String, tagList: List[Set[String]], quorumSize: Int = 1)

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)
  private[this] implicit val blaze: Resource[IO, Client[IO]] =  BlazeClientBuilder[IO](global).resource

  def checkNomadState(implicit interp: Http4sNomadClient[IO]): IO[NomadReadRaftConfigurationResponse] = {
    for {
      state <- NomadOp.nomadReadRaftConfiguration().foldMap(interp)
    } yield state
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
    } yield ServiceAddrs(consulAddr, nomadAddr)
  }

  def getTerraformWorkDir(is_integration: Boolean): File = {
    if(is_integration) new File("/tmp/radix/terraform") else new File("/opt/radix/terraform")
  }
  val execDir = "/opt/radix/timberland/terraform"
  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param featureFlags A map specifying which modules to enable
   * @param masterToken The access token used to communicate with consul/nomad
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(featureFlags: Map[String, Boolean],
                   masterToken: String,
                   integrationTest: Boolean
                  )(implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val mkTmpDirCommand = Seq("bash", "-c", "rm -rf /tmp/radix && mkdir -p /tmp/radix/terraform")

    val prefix = if(integrationTest) "integration-" else {
      val home = sys.env.getOrElse("HOME", "")
      val file = new File(s"$home/monorepo")
      if (file.exists) Process(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"), Some(new File(s"$home/monorepo"))).!!.trim.toLowerCase.replaceAll("/", "-") + "-" else ""
    }

    val variables =
      s"-var='prefix=$prefix' " +
      s"-var='test=$integrationTest' " +
      s"-var='acl_token=$masterToken' " +
      s"-var='consul_address=${serviceAddrs.consulAddr}' " +
      s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
      s"""-var='feature_flags=["${featureFlags.filter(_._2).keys.mkString("""","""")}"]' """

    val mkTmpDir = for {
      mkDirExitCode <- IO(Process(mkTmpDirCommand) !)
    } yield mkDirExitCode

    val apply = for {
      vaultToken <- IO((new VaultUtils).findVaultToken)
      getCredsFromUser <- timeoutTo(
        askUser("[Optional] AWS creds formatted like: aws_secret_access_key=foo aws_access_key_id=bar"),
        30.seconds,
        IO.pure("")
      )
      putCredsInVault <- IO {
        val creds = getCredsFromUser match {
          case "" => "input=empty_string"
          case null => "input=literal_null"
          case x => x
        }
        // NOTE: This makes the assumption that vaultAddr == consulAddr
        os.proc("/opt/radix/timberland/vault/vault",
          "kv",
          "put",
          s"-address=http://${serviceAddrs.consulAddr}:8200",
          "secret/aws/s3",
          s"$creds").call(stdout = os.Inherit, stderr = os.Inherit, env = Map ("VAULT_TOKEN" -> vaultToken))
      }
      hasAWSCreds <- checkVaultHasCredentials(vaultToken, "aws/s3", "aws_access_key_id")
      haveUpstreamCreds = s"-var=have_upstream_creds=$hasAWSCreds "
      //TODO don't spawn another bash shell
      applyCommand = Seq("bash", "-c", s"$execDir/terraform apply -auto-approve " + variables + haveUpstreamCreds)
      _ <- IO(Console.println(s"Running apply command in ${workingDir}: ${applyCommand.mkString(" ")}"))
      applyExitCode <- IO(Process(applyCommand, Some(workingDir)) !)
    } yield applyExitCode

    if(integrationTest)
      mkTmpDir *> initTerraform(integrationTest, Some(masterToken)) *> apply
    else
      initTerraform(integrationTest, Some(masterToken)) *> apply
  }

  def checkVaultHasCredentials(token: String, path: String, contains: String)
                              (implicit serviceAddrs: ServiceAddrs): IO[Boolean] = {
    blaze.use(implicit client => {
      val vaultUri = Uri.fromString(s"http://vault.service.consul:8200").toOption.get
      val vaultSession: VaultSession[IO] = new VaultSession[IO](authToken = Some(token),
        baseUrl = vaultUri,
        blazeClient = client)
      for {
        getSecret <- vaultSession.getSecret(path)
        result = getSecret match {
          case Left(y) => false
          case Right(x) => x.data.toString().contains(contains)
        }
      } yield result
    })
  }

  /**
   * Runs the terraform init command
   * @param integrationTest Runs init in a temporary directory
   * @param backendMasterToken ACL token to access consul. If this isn't specified, terraform won't connect to consul
   * @param serviceAddrs Contains addresses for consul and nomad
   * @return Nothing
   */
  def initTerraform(integrationTest: Boolean, backendMasterToken: Option[String])
                   (implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Unit] = {
    val backendVars = if (backendMasterToken.isDefined) Seq(
      s"-backend-config=address=${serviceAddrs.consulAddr}:8500",
      s"-backend-config=access_token=${backendMasterToken.get}",
      s"-var='acl_token=${backendMasterToken.get}'"
    ) else Seq.empty
    val initAgainCommand = Seq(
      s"$execDir/terraform", "init",
      "-plugin-dir", s"$execDir/plugins",
      s"-backend=${backendMasterToken.isDefined}"
    ) ++ backendVars
    val initFirstCommand = initAgainCommand ++ Seq("-from-module", s"$execDir/main")
    val workingDir = getTerraformWorkDir(integrationTest)

    for {
      alreadyInitialized <- IO(workingDir.listFiles.nonEmpty)
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- if (backendMasterToken.isEmpty) IO.unit else IO {
        Console.println(s"Init command: ${initCommand.mkString(" ")}")
      }
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

  /**
   *
   * @param port The port number to check (on localhost)
   * @return Whether the port is up
   */
  def isPortUp(port: Int): IO[Boolean] = IO {
    val socket =
      try {
        val serverSocket = new ServerSocket(port)
        serverSocket.setReuseAddress(true)
        Some(serverSocket)
      } catch {
        case _: IOException => None
      }
    socket.map(_.close()).isEmpty
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

  private def askUser(question: String): IO[String] = IO.delay {
    Console.print(question + " ")
    StdIn.readLine()
  }
}
