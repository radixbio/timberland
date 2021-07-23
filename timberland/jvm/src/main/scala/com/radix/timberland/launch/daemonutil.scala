package com.radix.timberland.launch

import java.io.File
import java.net.UnknownHostException

import cats.effect.{ContextShift, IO, Timer}
import com.radix.timberland.flags.flagConfig
import com.radix.timberland.flags.hooks.{awsAuthConfig, oktaAuthConfig}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{Util, _}
import org.xbill.DNS

import scala.concurrent.ExecutionContext.Implicits.global

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val execDir = RadPath.runtime / "timberland" / "terraform"

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

  def readTerraformPlan( // is this still needed?
    execDir: os.Path,
    workingDir: os.Path,
    variables: String
  ): IO[Unit] = {
    IO {
      val tlsVarStr = terraformTLSVars().mkString(" ")
      val F = File.createTempFile("radix", ".plan")
      val planCommand = Seq("bash", "-c", s"${execDir / "terraform"} plan $tlsVarStr $variables -out=$F")
      val showCommand = s"${execDir / "terraform"} show -json $F".split(" ")
      val planout = Util.proc(planCommand).call(cwd = workingDir)
      val planshow = Util.proc(showCommand).call(cwd = workingDir)
      ()
    }
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

  private val namespaceFile = RadPath.runtime / "timberland" / "release-name.txt"

  def getNamespace(integration: Boolean): String = {
    if (integration) "integration" else os.read(namespaceFile).stripLineEnd
  }

  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param featureFlags    A map specifying which modules to enable
   * @param integrationTest Whether to run terraform for integration tests
   * @param namespace          An optional nomad namespace
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(
    featureFlags: Map[String, Boolean],
    integrationTest: Boolean = false,
    namespace: Option[String] = None
  )(
    implicit serviceAddrs: ServiceAddrs = ServiceAddrs(),
    tokens: AuthTokens
  ): IO[Int] = {
    val workingDir = getTerraformWorkDir(integrationTest)
    val mkTmpDir = IO({
      if (os.exists(RadPath.temp)) os.remove.all(RadPath.temp)
      os.makeDir.all(RadPath.temp / "terraform")
    })

    implicit val persistentDir: os.Path = RadPath.runtime / "timberland"
    implicit val tokensOption: Option[AuthTokens] = Some(tokens)

    val variables =
      s"-var='namespace=${getNamespace(integrationTest)}' " +
        s"-var='test=$integrationTest' " +
        s"-var='acl_token=${tokens.consulNomadToken}' " +
        s"-var='vault_token=${tokens.vaultToken}' " +
        s"-var='consul_address=${serviceAddrs.consulAddr}' " +
        s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
        s"-var='vault_address=${serviceAddrs.vaultAddr}' " +
        s"""-var='feature_flags=["${featureFlags.filter(_._2).keys.mkString("""","""")}"]' """

    val show: IO[Unit] = readTerraformPlan(execDir, workingDir, variables)

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
              stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
              stderr = os.ProcessOutput(LogTUI.stdErrs(s"terraform apply $configStr"))
            )
            .exitCode
        else 0
      )
    } yield applyExitCode

    // val _apply = apply //.handleErrorWith(err => IO {println(applyCommand)} *>LogTUI.endTUI(Some(err)))

    val proc = awsAuthConfig.writeMinioAndAWSCredsToVault *>
      oktaAuthConfig.writeOktaCredsToVault *>
      Investigator.investigateEverything(tokens.consulNomadToken) *>
      initTerraform(integrationTest, Some(tokens.consulNomadToken)) *>
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
      alreadyInitialized <- IO(if (os.exists(workingDir)) os.list(workingDir).nonEmpty else false) // culprit
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- IO(
        if (!System.getProperty("os.name").toLowerCase.contains("windows"))
          Util
            .proc(initCommand)
            .call(
              cwd = workingDir,
              stdout = os.ProcessOutput(LogTUI.writeLogFromStream),
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

  def waitForQuorum(featureFlags: Map[String, Boolean], integrationTest: Boolean = false): IO[Unit] = {
    Investigator.waitForInvestigations()
  }
}
