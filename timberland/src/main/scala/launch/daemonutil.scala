package com.radix.timberland.launch

import java.net.UnknownHostException
import cats.effect.{ContextShift, IO, Timer}
import com.radix.timberland.flags.{featureFlags, tfParser}
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime.AuthTokens
import com.radix.timberland.util.{Util, _}
import com.radix.timberland.{ConstPaths, EnvironmentVariables}
import org.xbill.DNS
import scribe.Level

import scala.concurrent.ExecutionContext.Implicits.global

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def getServiceIps: IO[ServiceAddrs] = {
    // this goes through the JVM and the host btw
    def queryDns(host: String) = IO {
      try {
        // what is this node's private IP address
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
    } yield ServiceAddrs(consulAddr, nomadAddr, consulAddr)
  }

  def terraformTLSVars(backendConfig: Boolean = false): Iterable[String] = {
    // stores all the SSL shit (in a directory)
    val tlsVars = Map(
      "tls_ca_file" -> EnvironmentVariables.envVars("TF_VAR_TLS_CA_FILE").toString,
      "tls_cert_file" -> EnvironmentVariables.envVars("TF_VAR_TLS_CERT_FILE").toString,
      "tls_key_file" -> EnvironmentVariables.envVars("TF_VAR_TLS_KEY_FILE").toString,
      "tls_nomad_cert_file" -> EnvironmentVariables.envVars("TF_VAR_TLS_NOMAD_CERT_FILE").toString,
      "tls_nomad_key_file" -> EnvironmentVariables.envVars("TF_VAR_TLS_NOMAD_KEY_FILE").toString,
    )
    //TODO check with @alex about moving these to the above format
    val tlsBackendConfig = Map(
      "ca_file" -> sys.env.getOrElse("TLS_CA", (ConstPaths.certDir / "ca" / "cert.pem").toString),
      "cert_file" -> sys.env.getOrElse("TLS_CERT", (ConstPaths.certDir / "cli" / "cert.pem").toString),
      "key_file" -> sys.env.getOrElse("TLS_KEY", (ConstPaths.certDir / "cli" / "key.pem").toString),
    )
    val vars = tlsVars.map(kv => s"-var='${kv._1}=${kv._2}'")
    if (backendConfig) vars ++ tlsBackendConfig.map(kv => s"-backend-config=${kv._1}=${kv._2}") else vars
  }

  /**
   * Start up the specified daemons (or all or a combination) based upon the passed parameters. Will immediately exit
   * after submitting the job to Nomad via Terraform.
   *
   * @param namespace          An optional nomad namespace
   * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
   *         AllDaemonsStarted
   */
  def runTerraform(
    namespace: Option[String] = None,
    datacenter: String = "dc1",
    shouldStop: Boolean = false,
  )(implicit
    serviceAddrs: ServiceAddrs = ServiceAddrs(),
    tokens: AuthTokens,
  ): IO[Int] = {
    val workingDir = RadPath.runtime / "terraform"

    implicit val tokensOption: Option[AuthTokens] = Some(tokens)

    val variables =
      s"-var='namespace=${os.read(ConstPaths.namespaceFile).stripLineEnd}' " +
        s"-var='datacenter=$datacenter' " +
        s"-var='acl_token=${tokens.consulNomadToken}' " +
        s"-var='vault_token=${tokens.vaultToken}' " +
        s"-var='consul_address=${serviceAddrs.consulAddr}' " +
        s"-var='nomad_address=${serviceAddrs.nomadAddr}' " +
        s"-var='vault_address=${serviceAddrs.vaultAddr}' " +
        s"-var-file='${RadPath.runtime / "config" / "flags.json"}' "

    val cmdName = if (shouldStop) "destroy" else "apply"
    val cmd = for {
      configFiles <- IO(os.list(ConstPaths.TF_CONFIG_DIR))
      moduleDefArgs = configFiles.map(path => s"-var-file='$path' ").mkString
      tlsVars = terraformTLSVars().mkString(" ")
      configStr = variables + moduleDefArgs + tlsVars
      applyCommand = Seq(
        "bash",
        "-c",
        s"${ConstPaths.execDir / "terraform"} $cmdName -no-color -auto-approve " + variables + configStr,
      )
      applyExitCode <- IO(
        if (!System.getProperty("os.name").toLowerCase.contains("windows"))
          Util
            .proc(applyCommand)
            .call(
              cwd = workingDir,
              stdout = Util.scribePipe(Level.Info),
              stderr = Util.scribePipe(Level.Error),
            )
            .exitCode
        else 0
      )
    } yield applyExitCode

    val preCmds = if (shouldStop) IO.unit else featureFlags.runHooks *> initTerraform(Some(tokens.consulNomadToken))
    preCmds *> cmd
  }

  /**
   * Runs the terraform init command
   *
   * @param backendMasterToken ACL token to access consul. If this isn't specified, terraform won't connect to consul
   * @param serviceAddrs       Contains addresses for consul and nomad
   * @return Nothing
   */
  def initTerraform(backendMasterToken: Option[String])(implicit
    serviceAddrs: ServiceAddrs = ServiceAddrs()
  ): IO[Unit] = {
    val backendVars =
      if (backendMasterToken.isDefined)
        Seq(
          s"-backend-config=address=${serviceAddrs.consulAddr}:8501",
          s"-backend-config=access_token=${backendMasterToken.get}",
          s"-var='acl_token=${backendMasterToken.get}'",
        ) ++ terraformTLSVars(backendConfig = true)
      else Seq.empty

    val initAgainCommand = Seq(
      s"${ConstPaths.execDir / "terraform"}",
      "init",
      "-plugin-dir",
      s"${ConstPaths.execDir / "plugins"}",
      s"-backend=${backendMasterToken.isDefined}",
    ) ++ backendVars
    val initFirstCommand = initAgainCommand ++ Seq("-from-module", s"${ConstPaths.execDir / "modules"}")

    for {
      alreadyInitialized <- IO(
        if (os.exists(ConstPaths.workingDir)) os.list(ConstPaths.workingDir).nonEmpty else false
      ) // culprit
      initCommand = if (alreadyInitialized) initAgainCommand else initFirstCommand
      _ <- IO(
        if (!System.getProperty("os.name").toLowerCase.contains("windows"))
          Util
            .proc(initCommand)
            .call(
              cwd = ConstPaths.workingDir,
              stdout = Util.scribePipe(),
              stderr = Util.scribePipe(Level.Error),
              check = false,
            )
      )
    } yield ()
  }
}
