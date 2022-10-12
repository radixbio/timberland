package com.radix.timberland.runtime

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.radix.timberland.radixdefs.{ACLTokens, ServiceAddrs}
import com.radix.timberland.util.{RadPath, Util}
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.ConsulOp
import com.radix.utils.helm.NomadHCL.syntax._
import com.radix.utils.helm.NomadHCL.defs._
import com.radix.utils.helm.http4s.{Http4sConsulClient, Http4sNomadClient}
import com.radix.utils.helm.vault.{CertificateResponse, VaultError}
import com.radix.utils.tls.ConsulVaultSSLContext.{blaze, makeBlaze}
import com.radix.utils.tls.TrustEveryoneSSLContext.insecureBlaze
import io.circe._
import io.circe.parser._
import org.http4s.Uri
import org.http4s.client.Client

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration.DurationInt

object wan {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  private val localUri = Uri.unsafeFromString("https://127.0.0.1:8501")
  private val consulBin = (RadPath.persistentDir / "consul" / "consul").toString()

  def prepareWanHost(publicAddr: String): IO[Unit] = for {
    authTokens <- auth.getAuthTokens()
    _ <- storePublicAddr(publicAddr, authTokens)
    dcName <- getDatacenter(authTokens, localUri, blaze)
    _ <- setConsulConfigValue("primary_datacenter", Json.fromString(dcName))
    _ <- setConsulConfigValue("advertise_addr_wan", Json.fromString(publicAddr))
    _ <- Services.serviceController.restartConsul()
    _ <- mkMeshGateway(authTokens, publicAddr, primaryGatewayHost = None, dcName)
  } yield ()

  def wanJoin(
    leaderNode: Option[String],
    username: Option[String],
    password: Option[String],
    publicAddr: String,
  ): IO[Unit] = for {
    remoteAddr <- IO {
      leaderNode.getOrElse {
        scribe.error("No leader node specified.")
        sys.exit(1)
      }
    }
    serviceAddrs = ServiceAddrs(remoteAddr, remoteAddr, remoteAddr)
    localAuthTokens <- auth.getAuthTokens()
    leaderAuthTokens <- auth.getAuthTokens(isRemote = true, serviceAddrs, username, password)
    gossipKey <- auth.getGossipKey(leaderAuthTokens.vaultToken, remoteAddr, insecureBlaze)
    _ <- storePublicAddr(publicAddr, localAuthTokens)
    remoteUri = Uri.unsafeFromString(s"https://$remoteAddr:8501")
    consul = new Http4sConsulClient[IO](localUri, Some(localAuthTokens.consulNomadToken))
    localDcName <- getDatacenter(localAuthTokens, localUri, blaze)
    remoteBlaze <- mkRemoteBlaze(remoteAddr, leaderAuthTokens.vaultToken, localDcName)
    dcName <- getDatacenter(leaderAuthTokens, remoteUri, remoteBlaze)
    _ <- setConsulConfigValue("primary_datacenter", Json.fromString(dcName))
    _ <- consul(ConsulOp.KeyringInstallKey(gossipKey))
    _ <- consul(ConsulOp.KeyringSetPrimaryKey(gossipKey))
    _ <- setConsulConfigValue("retry_join_wan", Json.arr(Json.fromString(remoteAddr)))
    _ <- setConsulConfigValue(List("acl", "tokens", "replication"), Json.fromString(leaderAuthTokens.consulNomadToken))
    _ <- setConsulConfigValue(List("acl", "tokens", "agent"), Json.fromString(leaderAuthTokens.consulNomadToken))
    _ <- setConsulConfigValue(List("acl", "enable_token_replication"), Json.fromBoolean(true))
    _ <- Services.serviceController.restartConsul()
    // This node now uses leader's consul/nomad tokens
    vaultUri = Uri.unsafeFromString("https://127.0.0.1:8200")
    localVault = new VaultSession[IO](authToken = Some(localAuthTokens.vaultToken), baseUrl = vaultUri)
    _ <- auth.storeTokenInVault("consul-ui-token", leaderAuthTokens.consulNomadToken, localVault)
    _ <- auth.storeTokenInVault("actor-token", leaderAuthTokens.actorToken, localVault)
    _ <- rebootstrapNomad()
    _ <- replicateRootCerts(remoteAddr, leaderAuthTokens, dcName, localDcName)
    _ <- Services.serviceController.restartConsul()
    _ <- IO(scribe.info("Waiting for nodes to federate..."))
    _ <- waitUntilWanJoin(consul, dcName)
    _ <- mkMeshGateway(localAuthTokens, publicAddr, Some(remoteAddr), localDcName)
  } yield ()

  private def storePublicAddr(publicAddr: String, authTokens: AuthTokens): IO[Unit] = {
    val consul = new Http4sConsulClient[IO](localUri, Some(authTokens.consulNomadToken))
    consul.kvSet("publicAddress", publicAddr.getBytes("UTF-8"))
  }

  private def mkMeshGateway(
    authTokens: AuthTokens,
    localAddr: String,
    primaryGatewayHost: Option[String],
    dcName: String,
  ): IO[Unit] = for {
    _ <- IO(scribe.info("Making mesh gateway"))
    // TODO: Deploy mesh gateway jobspec here
    _ <- setConsulConfigValue(List("connect", "enable_mesh_gateway_wan_federation"), Json.fromBoolean(true))
    _ <- primaryGatewayHost match {
      case Some(host) => // Only run on host
        setConsulConfigValue("retry_join_wan", Json.arr()) *>
          setConsulConfigValue("primary_gateways", Json.arr(Json.fromString(s"$host:8443")))
      case None => IO.unit
    }
    _ <- Services.serviceController.restartConsul()
    _ <- Util.waitForConsul(authTokens.consulNomadToken, 30.seconds)
    _ <- IO.sleep(20.seconds) // Wait for ACL system to bootstrap
    _ <- if (primaryGatewayHost.nonEmpty) migrateConsulDefaultToken(authTokens.consulNomadToken) else IO.unit
    _ <- deployMeshGatewayJobspec(authTokens.consulNomadToken, dcName, localAddr)
  } yield ()

  private def deployMeshGatewayJobspec(token: String, dc: String, wanAddr: String): IO[Unit] = blaze.use { client =>
    val job = JobShim(
      "mesh-gateway",
      Job(
        datacenters = List(dc),
        `type` = "system",
        vault = Some(
          Vault(
            change_signal = Some("SIGINT"),
            policies = Some(List("actor-acl-token")),
          )
        ),
        group = List(
          GroupShim(
            "gateway",
            Group(
              network = Some(
                Network(
                  ports = List.empty,
                  mode = Some("host"),
                )
              ),
              task = List(
                TaskShim(
                  "gateway",
                  Task(
                    template = Some(
                      List(
                        TemplateShim(
                          Template(
                            data = Some(
                              List(
                                """{{with secret "secret/tokens/actor-token"}}""",
                                "{{if .Data.data.token}}",
                                """ACCESS_TOKEN = "{{.Data.data.token}}"""",
                                "{{end}}",
                                "{{end}}",
                                "",
                              ).mkString("\\n").replaceAll("\"", "\\\\\"")
                            ),
                            destination = "secrets/file.env",
                            env = true,
                          )
                        )
                      )
                    ),
                    env = Some(
                      Map(
                        "CONSUL_HTTP_SSL" -> "true",
                        "CONSUL_HTTP_ADDR" -> "127.0.0.1:8501",
                        "CONSUL_GRPC_ADDR" -> "https://127.0.0.1:8502",
                      )
                    ),
                    driver = "raw_exec",
                    config = Some(
                      DockerConfig( // Not actually a docker config
                        dns_servers = None,
                        command = Some((RadPath.persistentDir / "consul" / "consul").toString()),
                        args = Some(
                          List(
                            "connect",
                            "envoy",
                            "-token",
                            "${ACCESS_TOKEN}",
                            "-ca-file",
                            (RadPath.runtime / "certs" / "ca" / "combined.pem").toString(),
                            "-client-cert",
                            (RadPath.runtime / "certs" / "cli" / "cert.pem").toString(),
                            "-client-key",
                            (RadPath.runtime / "certs" / "cli" / "key.pem").toString(),
                            "-gateway",
                            "mesh",
                            "-expose-servers",
                            "-register",
                            "-service",
                            s"gateway-$dc",
                            "-address",
                            "127.0.0.1:8443",
                            "-wan-address",
                            s"$wanAddr:8443",
                          )
                        ),
                      )
                    ),
                  ),
                )
              ),
            ),
          )
        ),
      ),
    )
    val nomadUri = Uri.unsafeFromString(s"https://127.0.0.1:4646")
    val nomad = new Http4sNomadClient[IO](nomadUri, client, Some(token))
    nomad.nomadCreateJobFromHCL(job).void
  }

  private def waitUntilWanJoin(consul: Http4sConsulClient[IO], dc: String): IO[Unit] =
    consul.isConnectedToDatacenter(dc).handleErrorWith(_ => IO.pure(false)).map {
      case true  => ()
      case false => IO.sleep(5.seconds) *> waitUntilWanJoin(consul, dc)
    }

  private def setConsulConfigValue(key: String, value: Json): IO[Unit] = setConsulConfigValue(List(key), value)
  private def setConsulConfigValue(key: List[String], value: Json): IO[Unit] = {
    val cfgPath = RadPath.persistentDir / "consul"
    val cfgFiles = List(
      cfgPath / "consul-server.json",
      cfgPath / "consul-client.json",
      cfgPath / "config" / "consul.json",
    )
    cfgFiles
      .map { cfgFile =>
        for {
          jsonTxt <- IO(os.read(cfgFile))
          json = parse(jsonTxt).getOrElse {
            scribe.error(s"Failed to parse $cfgFile")
            sys.exit(1)
          }
          delta = key.foldRight(value)((k, v) => Json.obj(k -> v))
          newJson = json.deepMerge(delta)
          _ <- IO(os.write.over(cfgFile, newJson.spaces2))
        } yield ()
      }
      .parSequence
      .void
  }

  private def getDatacenter(tokens: AuthTokens, consulAddr: Uri, blazeRsc: Resource[IO, Client[IO]]): IO[String] = {
    val effect = IO.ioConcurrentEffect
    val consul = new Http4sConsulClient[IO](consulAddr, Some(tokens.consulNomadToken))(effect, blazeRsc)
    consul.agentGetInfo().map(_.datacenter)
  }

  // Nomad has to be rebootstrapped using this janky method in order to use the same token as the leader dc's consul
  private def rebootstrapNomad(): IO[Unit] = {
    val bootstrapResetFile = RadPath.runtime / "nomad" / "server" / "acl-bootstrap-reset"
    val bootstrapCmd = List(
      RadPath.nomadExec.toString(),
      "acl",
      "bootstrap",
      "-tls-skip-verify",
      "-address",
      "https://127.0.0.1:4646",
      (RadPath.persistentDir / ".acl-token").toString(),
    )
    for {
      resetIndexCmd <- Util.execArr(bootstrapCmd)
      resetIdxPattern = ".+reset index: ([0-9]+).+".r
      resetIndex = resetIndexCmd.stderr.stripLineEnd match { case resetIdxPattern(idx) => idx }
      _ <- IO(os.write(bootstrapResetFile, resetIndex))
      procout <- Util.execArr(bootstrapCmd)
      _ <- IO {
        if (procout.exitCode != 0) {
          scribe.error(s"Failed to rebootstrap nomad")
          scribe.error(s"STDOUT: ${procout.stdout}")
          scribe.error(s"STDERR: ${procout.stderr}")
        }
      }
      _ <- IO(os.remove(bootstrapResetFile))
    } yield ()
  }

  private def mkRemoteBlaze(vaultAddress: String, vaultToken: String, dcName: String): IO[Resource[IO, Client[IO]]] =
    getRemoteCerts(vaultAddress, vaultToken, dcName).map { certData =>
      makeBlaze(
        caPem = Some(certData.issuingCa),
        certPem = Some(certData.certificate),
        keyPem = Some(certData.privateKey),
      )
    }

  private def replicateRootCerts(remoteAddr: String, remoteAuth: AuthTokens, dc: String, localDc: String): IO[Unit] = {
    for {
      remoteCerts <- getRemoteCerts(remoteAddr, remoteAuth.vaultToken, localDc)
      caFile <- IO(os.temp(remoteCerts.issuingCa))
      certFile <- IO(os.temp(remoteCerts.certificate))
      keyFile <- IO(os.temp(remoteCerts.privateKey))
      _ <- IO(os.write.over(RadPath.runtime / "certs" / "ca" / s"$dc.pem", remoteCerts.issuingCa))
      localCaCert <- IO(os.read(RadPath.runtime / "certs" / "ca" / "cert.pem"))
      remoteDestCaCert = RadPath.runtime / "certs" / "ca" / s"$localDc.pem"
      remoteCommands =
        List(s"rm -rf $remoteDestCaCert") ++
          localCaCert.split('\n').map(line => s"""echo "$line" >> $remoteDestCaCert""") ++
          List("kill -HUP $(/bin/systemctl show -p MainPID consul | cut -c 9-)")
      // _ <- IO(println("RUNNING COMMANDS:\n" + remoteCommands.mkString("\n")))
      // Running consul exec over http is undocumented and wireshark shows it's complicated, so cli is used instead
      cmd = os.proc(
        List(
          consulBin,
          "exec",
          "-http-addr",
          s"https://$remoteAddr:8501",
          "-token",
          remoteAuth.consulNomadToken,
          "-ca-file",
          caFile.toString(),
          "-client-cert",
          certFile.toString(),
          "-client-key",
          keyFile.toString(),
          "-",
        )
      )
      _ <- Services.serviceController.refreshConsul()
      cmdRes <- IO(cmd.call(stdin = remoteCommands.mkString("\n"), check = false, stdout = os.Pipe, stderr = os.Pipe))
      _ <-
        if (cmdRes.exitCode != 0) IO {
          val stdout: String = cmdRes.out.text
          val stderr: String = cmdRes.err.text
          scribe.info(
            s" got result code: ${cmdRes.exitCode}, stderr: $stderr (${stderr.length}), stdout: $stdout (${stdout.length})"
          )
        }
        else IO.unit
    } yield ()
  }

  private def getRemoteCerts(vaultAddress: String, vaultToken: String, dcName: String): IO[CertificateResponse] = {
    val vaultUri = Uri.unsafeFromString(s"https://$vaultAddress:8200")
    val vault =
      new VaultSession[IO](authToken = Some(vaultToken), baseUrl = vaultUri)(IO.ioConcurrentEffect, insecureBlaze)
    vault.getCertificate("pki_int", s"cli.$dcName.consul", "24h").map {
      case Right(certData) => certData
      case Left(err) =>
        scribe.error(s"Failed to get certificate: $err")
        sys.exit(1)
    }
  }

  private def migrateConsulDefaultToken(masterToken: String): IO[Unit] = {
    val consul = new Http4sConsulClient[IO](localUri, Some(masterToken))
    for {
      tokens <- consul.aclGetTokens()
      defaultToken = tokens.find(_.description == "'Default-allow-DNS'").get
      _ <- consul.agentSetToken("default", defaultToken.secretId)
    } yield ()
  }
}
