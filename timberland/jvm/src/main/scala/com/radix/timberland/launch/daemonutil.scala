package com.radix.timberland.launch

import java.io.File
import java.net.UnknownHostException

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.flags.flagConfig
import com.radix.timberland.flags.hooks.{awsAuthConfig, oktaAuthConfig}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{Util, _}
import com.radix.utils.helm.http4s.Http4sNomadClient
import com.radix.utils.helm.{NomadOp, NomadReadRaftConfigurationResponse}
import io.circe.parser.parse
import io.circe.{DecodingFailure, Json}
import org.xbill.DNS

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

sealed trait DaemonState

case object AllDaemonsStarted extends DaemonState

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val execDir = RadPath.runtime / "timberland" / "terraform"

  /**
   * A map from feature flag to a list of services associated with that flag
   * This is used to determine when features have finished starting up
   */
  val flagServiceMap = Map(
    "zookeeper" -> Vector(
      "zookeeper-client-0",
      "zookeeper-follower-0",
      "zookeeper-othersrvs-0"
    ),
    "minio" -> Vector(
      "minio-local-service"
    ),
    "apprise" -> Vector(
      "apprise"
    ),
    "kafka_companions" -> Vector(
      "kc-schema-registry-service-0",
      "kc-rest-proxy-service-0",
      "kc-connect-service-0",
      "kc-ksql-service-0"
    ),
    "kafka" -> Vector(
      "kafka-0"
    ),
    "yugabyte" -> Vector(
      "yb-masters-rpc-0",
      "yb-tserver-connect-0"
    ),
    "elasticsearch" -> Vector(
      "es-rest-0",
      "es-transport-0",
      "kibana"
    ),
    "elemental" -> Vector(
      "elemental-machines-em-em"
    )
  )

  def checkNomadState(implicit interp: Http4sNomadClient[IO]): IO[NomadReadRaftConfigurationResponse] = {
    for {
      state <- NomadOp.nomadReadRaftConfiguration().foldMap(interp)
    } yield state
  }

  def getServiceIps(): IO[ServiceAddrs] = {
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

    // TODO(alex): what to do if consulAddr != vaultAddr? since vault usually doesn't resolve
    for {
      consulAddr <- queryDns("consul.service.consul")
      nomadAddr <- queryDns("nomad.service.consul")
    } yield ServiceAddrs(consulAddr, nomadAddr, consulAddr)
  }

  def readTerraformPlan(
    execDir: os.Path,
    workingDir: os.Path,
    variables: String
  ): IO[(TerraformMagic.TerraformPlan, Map[String, List[String]])] = {
    IO {
      val tlsVarStr = terraformTLSVars().mkString(" ")
      val F = File.createTempFile("radix", ".plan")
      val planCommand = Seq("bash", "-c", s"${execDir / "terraform"} plan $tlsVarStr $variables -out=$F")
      val showCommand = s"${execDir / "terraform"} show -json $F".split(" ")
      val planout = Util.proc(planCommand).call(cwd = workingDir)
      val planshow = Util.proc(showCommand).call(cwd = workingDir)

      val shown = new String(planshow.out.bytes)
      parse(shown) match {
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

        type Err[A] = Either[DecodingFailure, A]
        val resource_changes = x.hcursor.downField("resource_changes").as[Array[Json]] match {
          case Left(fail)  => Array.empty[Json] // Should only occur if no changes are being made?
          case Right(data) => data
        }
        val prog = for {
          //extract changed resources
//          resource_changes <- x.hcursor.downField("resource_changes").as[Array[Json]]
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
              for {
                submod <- x.hcursor
                  .downField("configuration")
                  .downField(m)
                  .downField("module_calls")
                  .keys
                  .getOrElse(List.empty)
              } yield (
                x.hcursor
                  .downField("configuration")
                  //be generic about modules
                  .downField(m)
                  .downField("module_calls")
                  .downField(submod)
                  .downField("module")
                  .downField("resources")
                  .as[Array[Json]]
                  .flatMap(
                    _.map(resource =>
                      for {
                        addr <- resource.hcursor.get[String]("address")
                        deps <- resource.hcursor.getOrElse("depends_on")(List.empty[String])
                      } yield (addr, deps)
                    ).toList.sequence[Err, (String, List[String])]
                  )
                )
            })
            .toList
            .sequence[Err, List[(String, List[String])]]
            //WARN: Intellij syntax hightlighting and typing gets funky here. It works though!
            // hand over only the relavant keys
            .map(_.flatten.toMap): Either[DecodingFailure, Map[String, List[String]]]
//              .filterKeys(withoutindex.toSet.contains) // TODO Disabling this for now as it filtered out everything.  Fix?
//              .mapValues(_.filter(withoutindex.toSet.contains))): Either[DecodingFailure, Map[String, List[String]]]
        } yield {
          (
            addresses.zip(actions).flatMap(x => x._2.map(y => (y, x._1))).groupBy(_._1).mapValues(_.map(_._2)),
            dependencies
          )
        }

        prog match {
          case Left(err) => IO.raiseError(err)
          case Right(v)  => IO.pure(v)
        }
      })
      //      .map(_.toMap)
      .map({
        case (plan, deps) => {
          (
            TerraformMagic.TerraformPlan(
              plan.getOrElse("create", List.empty).toSet,
              plan.getOrElse("read", List.empty).toSet,
              plan.getOrElse("update", List.empty).toSet,
              plan.getOrElse("delete", List.empty).toSet,
              plan.getOrElse("no-op", List.empty).toSet
            ),
            deps
          )
        }
      })
  }

  def terraformTLSVars(backendConfig: Boolean = false): Iterable[String] = {
    val certDir: os.Path = RadPath.runtime / "certs"
    val tlsVars = Map(
      "tls_ca_file" -> sys.env.getOrElse("TLS_CA", (certDir / "ca" / "cert.pem").toString),
      "tls_cert_file" -> sys.env.getOrElse("TLS_CERT", (certDir / "cli" / "cert.pem").toString),
      "tls_key_file" -> sys.env.getOrElse("TLS_KEY", (certDir / "cli" / "key.pem").toString),
      "tls_nomad_cert_file" -> sys.env.getOrElse("TLS_NOMAD_CERT", (certDir / "nomad" / "cli-cert.pem").toString),
      "tls_nomad_key_file" -> sys.env.getOrElse("TLS_NOMAD_KEY", (certDir / "nomad" / "cli-key.pem").toString)
    )
    val tlsBackendConfig = Map(
      "ca_file" -> sys.env.getOrElse("TLS_CA", (certDir / "ca" / "cert.pem").toString),
      "cert_file" -> sys.env.getOrElse("TLS_CERT", (certDir / "cli" / "cert.pem").toString),
      "key_file" -> sys.env.getOrElse("TLS_KEY", (certDir / "cli" / "key.pem").toString)
    )
    val vars = tlsVars.map(kv => s"-var='${kv._1}=${kv._2}'")
    if (backendConfig) vars ++ tlsBackendConfig.map(kv => s"-backend-config=${kv._1}=${kv._2}") else vars
  }

  def getTerraformWorkDir(is_integration: Boolean): os.Path = {
    if (is_integration) RadPath.temp / "terraform" else os.root / "opt" / "radix" / "terraform"
  }

  private val prefixFile = RadPath.runtime / "timberland" / "git-branch-workspace-status.txt"

  def readPrefixFile: String = {
    os.read(prefixFile).stripLineEnd
  }

  def getPrefix(integration: Boolean): String = {
    if (integration) "integration-" else sanitizePrefix(sys.env.getOrElse("NOMAD_PREFIX", readPrefixFile))
  }

  private def sanitizePrefix(rawPrefix: String): String = {
    val cutPrefix = if (!rawPrefix.isEmpty) rawPrefix.substring(0, Math.min(rawPrefix.length, 25)) + "-" else rawPrefix
    if (cutPrefix.matches("[a-zA-Z\\d-]*")) cutPrefix
    else {
      cutPrefix.replaceAll("_", "-").replaceAll("[^a-zA-Z\\d-]", "")
    }
  }

  private def updatePrefixFile(prefix: Option[String]): Unit = {
    prefix match {
      case Some(str) => {
        val sanitized = sanitizePrefix(str)
        if (!str.equals(sanitized)) LogTUI.printAfter(s"The given prefix $str was invalid; used $sanitized instead.")
        os.write.over(prefixFile, sanitized)
      }
      case None => ()
    }
  }

  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param featureFlags    A map specifying which modules to enable
   * @param integrationTest Whether to run terraform for integration tests
   * @param prefix          An optional prefix to prepend to job names
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(featureFlags: Map[String, Boolean], integrationTest: Boolean = false, prefix: Option[String] = None)(
    implicit serviceAddrs: ServiceAddrs = ServiceAddrs(),
    tokens: AuthTokens
  ): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val mkTmpDir = IO({
      if (os.exists(RadPath.temp)) os.remove.all(RadPath.temp)
      os.makeDir.all(RadPath.temp / "terraform")
    })

    updatePrefixFile(prefix)

    implicit val persistentDir: os.Path = RadPath.runtime / "timberland"
    implicit val tokensOption: Option[AuthTokens] = Some(tokens)

    val variables =
      s"-var='prefix=${getPrefix(integrationTest)}' " +
        s"-var='test=$integrationTest' " +
        s"-var='acl_token=${tokens.consulNomadToken}' " +
        s"-var='vault_token=${tokens.vaultToken}' " +
        s"-var='consul_address=${serviceAddrs.consulAddr}' " +
        s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
        s"-var='vault_address=${serviceAddrs.vaultAddr}' " +
        s"""-var='feature_flags=["${featureFlags.filter(_._2).keys.mkString("""","""")}"]' """

    val show: IO[(TerraformMagic.TerraformPlan, Map[String, List[String]])] =
      readTerraformPlan(execDir, workingDir, variables)

    val apply = for {
      flagConfig <- flagConfig.updateFlagConfig(featureFlags)
      flagConfigStr = flagConfig.configVars.map(kv => s"-var='${kv._1}=${kv._2}'").mkString(" ")
      tlsConfigStr = terraformTLSVars().mkString(" ")
      definedVarsStr = s"""-var='defined_config_vars=["${flagConfig.definedVars.mkString("""","""")}"]'"""
      configStr = s"$flagConfigStr $tlsConfigStr $definedVarsStr "
      applyCommand = Seq(
        "bash",
        "-c",
        s"${execDir / "terraform"} apply -no-color -auto-approve " + variables + configStr
      )
      applyExitCode <- IO(
        if (!System.getProperty("os.name").toLowerCase.contains("windows"))
          Util
            .proc(applyCommand)
            .call(
              cwd = workingDir,
              stdout = os.ProcessOutput(LogTUI.tfapply),
              stderr = os.ProcessOutput(LogTUI.stdErrs("terraform-apply"))
            )
            .exitCode
        else 0
      )
    } yield applyExitCode

    val proc = awsAuthConfig.writeMinioAndAWSCredsToVault *>
      oktaAuthConfig.writeOktaCredsToVault *>
      initTerraform(integrationTest, Some(tokens.consulNomadToken)) *>
      show.flatMap(LogTUI.plan) *>
      apply
    if (integrationTest) mkTmpDir *> proc else proc
  }

  /**
   * Runs the terraform init command
   *
   * @param integrationTest    Runs init in a temporary directory
   * @param backendMasterToken ACL token to access consul. If this isn't specified, terraform won't connect to consul
   * @param serviceAddrs       Contains addresses for consul and nomad
   * @return Nothing
   */
  def initTerraform(integrationTest: Boolean, backendMasterToken: Option[String])(
    implicit serviceAddrs: ServiceAddrs = ServiceAddrs()
  ): IO[Unit] = {
    val backendVars =
      if (backendMasterToken.isDefined)
        Seq(
          s"-backend-config=address=${serviceAddrs.consulAddr}:8501",
          s"-backend-config=access_token=${backendMasterToken.get}",
          s"-var='acl_token=${backendMasterToken.get}'"
        ) ++ terraformTLSVars(backendConfig = true)
      else Seq.empty

    val initAgainCommand = Seq(
      s"${execDir / "terraform"}",
      "init",
      "-plugin-dir",
      s"${execDir / "plugins"}",
      s"-backend=${backendMasterToken.isDefined}"
    ) ++ backendVars
    val initFirstCommand = initAgainCommand ++ Seq("-from-module", s"${execDir / "modules"}")

    val workingDir = getTerraformWorkDir(integrationTest)

    for {
      alreadyInitialized <- IO(if (os.exists(workingDir)) os.list(workingDir).nonEmpty else false)
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- IO(
        if (!System.getProperty("os.name").toLowerCase.contains("windows"))
          Util
            .proc(initCommand)
            .call(
              cwd = workingDir,
              stdout = os.ProcessOutput(LogTUI.init),
              stderr = os.ProcessOutput(LogTUI.stdErrs("terraform-init")),
              check = false
            )
      )
    } yield ()
  }

  def stopTerraform(integrationTest: Boolean): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val cmd = Seq("bash", "-c", s"${execDir / "terraform"} destroy -auto-approve")
    scribe.trace(s"Destroy command ${cmd.mkString(" ")}")
    val ret = Util.proc(cmd).call(stdout = os.Inherit, stderr = os.Inherit, cwd = workingDir)
    IO(ret.exitCode)
  }

  def waitForQuorum(featureFlags: Map[String, Boolean], integrationTest: Boolean = false): IO[DaemonState] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

    val prefix = getPrefix(integrationTest)

    val enabledServices = featureFlags.toList.flatMap {
      case (feature, enabled) =>
        if (enabled) {
          flagServiceMap.getOrElse(feature, Vector())
        } else Vector()
    }

    enabledServices.parTraverse { service =>
      Util.waitForDNS(s"$service.service.consul", 5.minutes)
    } *> IO.pure(AllDaemonsStarted)
  }

}
