package com.radix.timberland.launch

import java.io.{File, FileWriter, IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, UnknownHostException}

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.radix.timberland.flags.flagConfig
import com.radix.timberland.util.{LogTUI, ResolveDNS, TerraformMagic, VaultUtils, WaitForDNS}
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
//import com.radix.timberland.runtime.flags
import io.circe.parser.parse
import io.circe.{DecodingFailure, Json}
import org.http4s.Uri
import org.http4s.client.Client
import os.{ProcessOutput, proc}

import scala.io.{Source, StdIn}

sealed trait DaemonState

case object AllDaemonsStarted extends DaemonState

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)
  private[this] implicit val blaze: Resource[IO, Client[IO]] =  BlazeClientBuilder[IO](global).resource

  /**
   * A map from feature flag to a list of services associated with that flag
   * This is used to determine when features have finished starting up
   */
  val flagServiceMap = Map(
    "zookeeper" -> Vector(
      "zookeeper-daemons-zookeeper-zookeeper",
    ),
    "minio" -> Vector(
      "minio-job-minio-group-nginx-minio",
      "minio-job-minio-group-minio-local",
    ),
    "apprise" -> Vector(
      "apprise-apprise-apprise",
    ),
    "kafka_companions" -> Vector(
      "kc-daemons-companions-kSQL",
      "kc-daemons-companions-connect",
      "kc-daemons-companions-rest-proxy",
      "kc-daemons-companions-schema-registry",
    ),
    "kafka" -> Vector(
      "kafka-daemons-kafka-kafka",
    ),
    "yugabyte" -> Vector(
      "yugabyte-yugabyte-ybmaster",
      "yugabyte-yugabyte-ybtserver",
    ),
    "elasticsearch" -> Vector(
      "elasticsearch-es-es-generic-node",
      "elasticsearch-kibana-kibana",
    ),
    "retool" -> Vector(
      "retool-retool-postgres",
      "retool-retool-retool-main",
    ),
    "elemental" -> Vector(
      "elemental-machines-em-em",
    ),
  )

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

//  def getTerraformWorkDir(is_integration: Boolean): File = {
//    if (is_integration) new File("/tmp/radix/terraform") else new File("/opt/radix/terraform")
//  }

  def updatePrefixFile(prefix: Option[String]): Unit = {
    prefix match {
      case Some(str) => {
        val file = "/opt/radix/timberland/git-branch-workspace-status.txt"
        val writer = new FileWriter(file)
        try writer.write(str) finally writer.close()
      }
      case None => ()
    }
  }


  def readTerraformPlan(execDir: String, workingDir: os.Path, variables: String): IO[(TerraformMagic.TerraformPlan, Map[String, List[String]])] = {
    IO {
      val F = File.createTempFile("radix", ".plan")
      val planCommand = Seq("bash", "-c", s"$execDir/terraform plan $variables -out=$F")
      val showCommand = s"$execDir/terraform show -json $F".split(" ")
      val planout = proc(planCommand).call(cwd = workingDir)
      val planshow = proc(showCommand).call(cwd = workingDir)
      parse(new String(planshow.out.bytes)) match {
        case Left(value)  => IO.raiseError(value)
        case Right(value) => IO.pure(value)
      }
    }.flatten
      .flatMap(x => {
        //        import cats._
        //        import cats.instances.all._
        //        import cats.implicits._
        import cats.instances.either._
        import cats.instances.list._
        import cats.syntax.traverse._
        import cats.syntax.either._

        type Err[A] = Either[DecodingFailure, A]
        val prog = for {
          //extract changed resources
          resource_changes <- x.hcursor.downField("resource_changes").as[Array[Json]]
          addresses <- resource_changes.map(_.hcursor.downField("address").as[String]).toList.sequence
          //strip off the [0] to get the actual addresses
          withoutindex = addresses.map(TerraformMagic.stripindex)
          actions <- resource_changes
            .map(_.hcursor.downField("change").downField("actions").as[List[String]])
            .toList
            .sequence
          // extract dependency structure of resources
          modules <- Right(x.hcursor.downField("configuration").keys.get.filterNot(_ == "provider_config"))
          dependencies <- modules
            .flatMap(m => {
              for {submod <- x.hcursor.downField("configuration").downField(m).downField("module_calls").keys.getOrElse(List.empty)}
                yield (
                  x.hcursor
                    .downField("configuration")
                    //be generic about modules
                    .downField(m)
                    .downField("module_calls")
                    .downField(submod)
                    .downField("module")
                    .downField("resources")
                    .as[Array[Json]]
                    .flatMap(_.map(resource =>
                      for {
                        addr <- resource.hcursor.get[String]("address")
                        deps <- resource.hcursor.getOrElse("depends_on")(List.empty[String])
                      } yield (addr, deps)).toList.sequence[Err, (String, List[String])])
                  )
            })
            .toList
            .sequence[Err, List[(String, List[String])]]
            //WARN: Intellij syntax hightlighting and typing gets funky here. It works though!
            // hand over only the relavant keys
            .map(_.flatten.toMap
              .filterKeys(withoutindex.toSet.contains)
              .mapValues(_.filter(withoutindex.toSet.contains))): Either[DecodingFailure, Map[String, List[String]]]
        } yield {
          (addresses.zip(actions).flatMap(x => x._2.map(y => (y, x._1))).groupBy(_._1).mapValues(_.map(_._2)), dependencies)
        }

        prog match {
          case Left(err) => IO.raiseError(err)
          case Right(v)  => IO.pure(v)
        }
      })
      //      .map(_.toMap)
      .map({case (plan, deps) => {
        (TerraformMagic.TerraformPlan(
          plan.getOrElse("create", List.empty).toSet,
          plan.getOrElse("read", List.empty).toSet,
          plan.getOrElse("update", List.empty).toSet,
          plan.getOrElse("delete", List.empty).toSet,
          plan.getOrElse("no-op", List.empty).toSet
        ), deps)
      }})
  }


  def getTerraformWorkDir(is_integration: Boolean): os.Path = {
    if (is_integration) os.root / "tmp" / "radix" / "terraform" else os.root / "opt" / "radix" / "terraform"
  }

  def getPrefix(integration: Boolean): String = {
    val rawPrefix = if (integration) "integration" else {
      sys.env.get("NOMAD_PREFIX") match {
        case Some(prefix) => prefix
        case None => {
          val bufferedSource = Source.fromFile("/opt/radix/timberland/git-branch-workspace-status.txt")
          val result = bufferedSource.getLines().mkString
          bufferedSource.close()
          if (result.length > 0) result else ""
        }
      }
    }

    val cutPrefix = if (rawPrefix.length > 0) rawPrefix.substring(0, Math.min(rawPrefix.length, 25)) + "-" else rawPrefix

    if(cutPrefix.matches("[a-zA-Z\\d-]*")) cutPrefix else {
      cutPrefix.replaceAll("_", "-").replaceAll("[^a-zA-Z\\d-]", "")
    }
  }

  val execDir = "/opt/radix/timberland/terraform"

  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param featureFlags A map specifying which modules to enable
   * @param masterToken  The access token used to communicate with consul/nomad
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(featureFlags: Map[String, Boolean],
                   masterToken: String,
                   integrationTest: Boolean,
                   prefix: Option[String]
                  )(implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val mkTmpDirCommand = Seq("bash", "-c", "rm -rf /tmp/radix && mkdir -p /tmp/radix/terraform")

    updatePrefixFile(prefix)

    implicit val persistentDir: os.Path = os.Path("/opt/radix/timberland")

    val variables =
      s"-var='prefix=${getPrefix(integrationTest)}' " +
        s"-var='test=$integrationTest' " +
        s"-var='acl_token=$masterToken' " +
        s"-var='consul_address=${serviceAddrs.consulAddr}' " +
        s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
        s"""-var='feature_flags=["${featureFlags.filter(_._2).keys.mkString("""","""")}"]' """

    val mkTmpDir = for {
      mkDirExitCode <- IO(Process(mkTmpDirCommand) !)
    } yield mkDirExitCode

    val show: IO[(TerraformMagic.TerraformPlan, Map[String, List[String]])] = readTerraformPlan(execDir,
                                                                                                workingDir,
                                                                                                variables)

    val apply = for {
      flagConfig <- flagConfig.updateFlagConfig(featureFlags, Some(masterToken))
      configEntriesStr = flagConfig.configVars.map(kv => s"-var='${kv._1}=${kv._2}'").mkString(" ")
      definedVarsStr = s"""-var='defined_config_vars=["${flagConfig.definedVars.mkString("""","""")}"]'"""
      configStr = s"$configEntriesStr $definedVarsStr "
      applyCommand = Seq("bash", "-c", s"$execDir/terraform apply -auto-approve " + variables + configStr)
      applyExitCode <- IO(os.proc(applyCommand).call(
        cwd = workingDir,
        stdout = os.ProcessOutput(LogTUI.tfapply),
        stderr = os.ProcessOutput(LogTUI.stdErrs("terraform-apply"))
      ).exitCode)
    } yield applyExitCode

    if(integrationTest)
      mkTmpDir *> initTerraform(integrationTest, Some(masterToken)) *> show.flatMap(LogTUI.plan) *> apply
    else
      initTerraform(integrationTest, Some(masterToken)) *> show.flatMap(LogTUI.plan) *> apply
  }

  /**
   * Runs the terraform init command
   *
   * @param integrationTest    Runs init in a temporary directory
   * @param backendMasterToken ACL token to access consul. If this isn't specified, terraform won't connect to consul
   * @param serviceAddrs       Contains addresses for consul and nomad
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
      alreadyInitialized <- IO(os.list(workingDir).nonEmpty)
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- IO(os.proc(initCommand).call(
        cwd = workingDir,
        stdout = ProcessOutput(LogTUI.init),
        stderr = ProcessOutput(LogTUI.stdErrs("terraform-init")),
        check = false))
    } yield ()
  }

  def stopTerraform(integrationTest: Boolean): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val cmd = Seq("bash", "-c", s"$execDir/terraform destroy -auto-approve")
    println(s"Destroy command ${cmd.mkString(" ")}")
    val ret = os.proc(cmd).call(stdout = os.Inherit, stderr = os.Inherit, cwd = workingDir)
    IO(ret.exitCode)
  }

  def waitForQuorum(featureFlags: Map[String, Boolean], integrationTest: Boolean = false): IO[DaemonState] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

    val prefix = getPrefix(integrationTest)

    val enabledServices = featureFlags.toList.flatMap {
      case (feature, enabled) => if (enabled) {
        flagServiceMap.getOrElse(feature, Vector()).map(name => prefix + name)
      } else Vector()
    }

    enabledServices.parTraverse { service =>
      waitForDNS(s"$service.service.consul", 5.minutes)
    } *> IO.pure(AllDaemonsStarted)
  }

  /** Wait for a DNS record to become available. Consul will not return a record for failing services.
   * This returns Unit because we do not care what the result is, only that there is at least one.
   *
   * @param dnsName         The DNS name to look up
   * @param timeoutDuration How long to wait before throwing an exception
   */
  def waitForDNS(dnsName: String, timeoutDuration: FiniteDuration): IO[Boolean] = {
    val dnsQuery = new DNS.Lookup(dnsName, DNS.Type.SRV, DNS.DClass.IN)
    def queryProg(): IO[Boolean] = for {
      _ <- IO(LogTUI.writeLog(s"checking: ${dnsName}"))
      dnsAnswers <- IO(Option(dnsQuery.run.toSeq).getOrElse(Seq.empty))
      result <- if (dnsQuery.getResult != DNS.Lookup.SUCCESSFUL || dnsAnswers.isEmpty)
        IO.sleep(1.seconds) *> queryProg
      else
        LogTUI.dns(ResolveDNS(dnsName)) *> IO.pure(true)
    } yield result

    LogTUI.dns(WaitForDNS(dnsName)) *> timeoutTo(queryProg, timeoutDuration, IO.pure(false))
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

  def waitForPortUp(port: Int, timeoutDuration: FiniteDuration): IO[Boolean] = {
    def queryProg(): IO[Boolean] = for {
      portUp <- isPortUp(port)
      _ <- if (!portUp) {
        IO.sleep(1.second) *> queryProg
      }
      else IO(portUp)
    } yield portUp
    timeout(queryProg, timeoutDuration)
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
}
