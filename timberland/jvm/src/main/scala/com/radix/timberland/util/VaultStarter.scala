package com.radix.timberland.util

import java.io.{File, PrintWriter}
import java.net.{InetAddress, UnknownHostException}

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.vault._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.implicits._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{TimeoutException, duration}

sealed trait VaultStatus

sealed trait VaultInitStatus extends VaultStatus

case object VaultNotInitalized extends VaultInitStatus

case class VaultInitialized(key: String, root_token: String) extends VaultInitStatus

case object VaultAlreadyInitialized extends VaultInitStatus

sealed trait VaultSealStatus extends VaultStatus

case object VaultSealed extends VaultSealStatus

case class VaultUnsealed(key: String, root_token: String)
  extends VaultSealStatus

case object VaultAlreadyUnsealed extends VaultSealStatus

sealed trait VaultOAuthStatus extends VaultStatus

case object VaultOauthPluginNotInstalled extends VaultOAuthStatus

case object VaultOauthPluginInstalled extends VaultOAuthStatus

case class RegisterProvider(provider: String, client_id: String, client_secret: String)


class VaultUtils {
  def storeVaultTokenKey(key: String, token: String): String = {
    // if HOME isn't set, use /tmp (file still owed / readable only by root)
    val prefix = "/opt/radix/timberland"
    val tokenFile = new File(prefix + "/.vault-token")
    val sealFile = new File(prefix + "/.vault-seal")
    val tokenWriter = new PrintWriter(tokenFile)
    val sealWriter = new PrintWriter(sealFile)
    tokenWriter.write(token)
    tokenWriter.close()
    sealWriter.write(key)
    sealWriter.close()
    // chmod 0400
    sealFile.setReadable(false, true)
    sealFile.setWritable(false, false)
    sealFile.setExecutable(false, false)
    // chmod 0400
    tokenFile.setReadable(false, true)
    tokenFile.setWritable(false, false)
    tokenFile.setExecutable(false, false)
    token
  }

  def findVaultKey(): String = {
    val token = sys.env.get("VAULT_SEAL") match {
      case Some(token) => token
      case None => {
        val source = scala.io.Source.fromFile("/opt/radix/timberland/.vault-seal")
        val lines = try source.mkString finally source.close()
        lines
      }
    }
    return token
  }

  def setupVault(): IO[Unit] = IO({
    println("setting up vault!")
    val vaultToken = findVaultToken()
    os.proc("/opt/radix/timberland/vault/vault",
      "write",
      "-address=http://127.0.0.1:8200",
      "/auth/token/roles/nomad-cluster",
      "@/opt/radix/timberland/vault/nomad-cluster-role.json").call(stdout = os.Inherit, stderr = os.Inherit, env = Map("VAULT_TOKEN" -> vaultToken))
    os.proc("/opt/radix/timberland/vault/vault",
      "secrets",
      "enable",
      "-address=http://127.0.0.1:8200",
      "-path=secret",
      "kv").call(stdout = os.Inherit, stderr = os.Inherit, env = Map("VAULT_TOKEN" -> vaultToken))
    List("nomad-server", "read-flag-config").map(policy =>
      os.proc("/opt/radix/timberland/vault/vault",
        "policy",
        "write",
        "-address=http://127.0.0.1:8200",
        s"${policy}",
        s"/opt/radix/timberland/vault/${policy}-policy.hcl").call(stdout = os.Inherit, stderr = os.Inherit, env = Map("VAULT_TOKEN" -> vaultToken))
    )
  })

  def findVaultToken(): String = {
    val token = sys.env.get("VAULT_TOKEN") match {
      case Some(token) => token
      case None => {
        val source = scala.io.Source.fromFile("/opt/radix/timberland/.vault-token")
        val lines = try source.mkString finally source.close()
        lines
      }
    }
    return token
  }
}

class VaultStarter {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)


  /** Wait for a DNS record to become available. Consul will not return a record for failing services.
   * This returns Unit because we do not care what the result is, only that there is at least one.
   *
   * @param dnsName         The DNS name to look up
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

  def checkVaultInitStatus(implicit vaultSession: VaultSession[IO]) = {
    for {
      initStatus <- vaultSession.sysInitStatus
      _ <- IO(scribe.trace(s"********* VAULT INIT STATUS: $initStatus"))
    } yield initStatus
  }

  def checkVaultSealStatus(implicit vaultSession: VaultSession[IO]) = {
    for {
      sealStatus <- vaultSession.sealStatus
      _ <- IO(scribe.trace(s"********* VAULT SEAL STATUS: $sealStatus"))
    } yield sealStatus
  }

  /** *
   * Recursively attempts to initialize Vault with 1 master key and 1 share, calling itself on fail.
   * Returns a Monad representing the Master Key and Vault token as VaultInitialized or an error state.
   *
   * @param vaultSession
   * @return IO[VaultInitStatus]
   */
  def waitOnVaultInit(
                       implicit vaultSession: VaultSession[IO]): IO[VaultInitStatus] = {
    for {
      _ <- IO.sleep(1.second)
      initStatus <- checkVaultInitStatus
      vaultInitStatus <- initStatus match {
        case Right(false) => {
          val initReq = InitRequest(1, 1)
          for {
            vaultResponse <- vaultSession.sysInit(initReq)
            initStatus <- vaultResponse match {
              case Right(status) => {
                IO(scribe.trace(s"******** VAULT KEY and TOKEN: ${status.keys(0)}, TOKEN: ${status.root_token}")) *>
                  IO.pure(VaultInitialized(status.keys(0), status.root_token))
              }
              case Left(status) => {
                IO(
                  scribe.trace(
                    s"***** VAULT ERROR: ${status.printStackTrace()}")) *>
                  waitOnVaultInit
              }
            }
          } yield initStatus
        }
        case Right(true) => IO.pure(VaultAlreadyInitialized)
        case Left(failure) => IO(scribe.trace(s"*****VAULT ERROR: ${failure.printStackTrace()}")) *> IO.pure(VaultNotInitalized)

      }
    } yield vaultInitStatus
  }

  /** *
   * Recursively attempts to unseal Vault, calling itself on fail.
   * Returns a Monad representing the Master Key and Vault token as VaultUnsealed or an error state.
   *
   * @param key
   * @param token
   * @param vaultSession
   * @return IO[VaultSealStatus] representing an unsealed Vault or a sealed Vault
   */
  def waitOnVaultUnseal(key: String, token: String)(
    implicit vaultSession: VaultSession[IO]): IO[VaultSealStatus] = {
    for {
      _ <- IO.sleep(1.second)
      sealStatus <- checkVaultSealStatus
      vaultSealResult <- sealStatus match {
        case Right(status) => {

          status.`sealed` match {
            case true => {
              val unsealRequest = UnsealRequest(key)
              for {
                _ <- IO(scribe.trace(s"Sending unseal request with key $key"))
                vaultResponse <- vaultSession.unseal(unsealRequest)
                _ <- IO(scribe.trace(s"======= Unseal response: $vaultResponse"))
                unsealResult <- vaultResponse match {
                  case Right(unsealStatus) => {
                    unsealStatus.`sealed` match {
                      case false => IO.pure(VaultUnsealed(key, token))
                      case true => IO.pure(VaultSealed)
                    }
                  }
                  case Left(error) => {
                    IO(scribe.trace(
                      s"######### Unseal Error: ${error.printStackTrace()}")) *> IO
                      .pure(VaultSealed)
                  }
                }

              } yield unsealResult
            }
            case false => IO.pure(VaultUnsealed(key, token))
          }

        }
        case Left(error) => {
          IO(scribe.trace(s"###### Unseal Error: ${error.printStackTrace}")) *> waitOnVaultUnseal(
            key,
            token)
        }
      }
    } yield vaultSealResult
  }

  /** *
   * Initialize Google OAuth plugin if creds are provided via envvars.
   *
   * @param token
   * @param baseUrl
   * @return IO[VaultOauthStatus]
   */
  def initializeGoogleOauthPlugin(token: String, baseUrl: Uri): IO[VaultOAuthStatus] = {
    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.trace("Registering Google Plugin...")

      implicit val vaultSession: VaultSession[IO] =
        new VaultSession[IO](authToken = Some(token), baseUrl = baseUrl, blazeClient = client)

      for {
        _ <- IO.sleep(2.seconds)

        registerPluginRequest = RegisterPluginRequest(
          "aece93ff2302b7ee5f90eebfbe8fe8296f1ce18f084c09823dbb3d3f0050b107",
          "vault-plugin-secrets-oauthapp")
        registerPluginResult <- vaultSession
          .registerPlugin(Secret(), "oauth2", registerPluginRequest)

        enableEngineRequest = EnableSecretsEngine("oauth2")
        enableEngineResult <- vaultSession
          .enableSecretsEngine("oauth2/google", enableEngineRequest)

        oauthConfigRequest = CreateSecretRequest(
          data = RegisterProvider("google",
            sys.env("GOOGLE_OAUTH_ID"),
            sys.env("GOOGLE_OAUTH_SECRET")).asJson,
          cas = None)
        writeOauthConfig <- vaultSession
          .createOauthSecret("oauth2/google/config", oauthConfigRequest)
      } yield VaultOauthPluginInstalled
    })
  }

  def initializeAndUnsealVault(baseUrl: Uri): IO[VaultSealStatus] = {
    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.trace("Checking Vault Status...")

      scribe.trace(s"VAULT BASE URL: $baseUrl")

      implicit val vaultSession: VaultSession[IO] =
        new VaultSession[IO](baseUrl = baseUrl, blazeClient = client, authToken = None)
      val vaultUtils = new VaultUtils
      val result = for {
        vaultInit <- waitOnVaultInit
        _ <- IO(scribe.info(s"vault init: $vaultInit"))
        finalState <- vaultInit match {
          case VaultNotInitalized => IO.pure(VaultSealed)
          case VaultInitialized(key, root_token) => {
            for {
              _ <- IO(vaultUtils.storeVaultTokenKey(key, root_token))
              vaultResp <- waitOnVaultUnseal(key, root_token)
              setupVault <- vaultUtils.setupVault()
            } yield vaultResp
          }
          case VaultAlreadyInitialized => {
            for {
              vaultToken <- IO(vaultUtils.findVaultToken)
              vaultKey <- IO(vaultUtils.findVaultKey)
              sealStatus <- checkVaultSealStatus
              seal <- sealStatus match {
                case Right(status) =>
                  status.`sealed` match {
                    case false => IO.pure(VaultAlreadyUnsealed)
                    case true => waitOnVaultUnseal(vaultKey, vaultToken)
                  }
                case Left(error) => IO.pure(VaultSealed)
              }
            } yield seal
          }
        }
      } yield finalState
      result.attempt.flatMap {
        case Left(a) =>
          a.printStackTrace()
          timeout(initializeAndUnsealVault(baseUrl), 1.minutes)
        case Right(finalState) => IO.pure(finalState)
      }
    })
  }

  /** *
   * Initialize, unseal, and setup Vault in an idempotent manner, with error handling and logging.
   *
   * @return IO[String] - the Vault token.
   */
  def initializeAndUnsealAndSetupVault()(implicit serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[String] = {
    val vaultBaseUrl = uri"http://127.0.0.1:8200"
    val starter = new VaultStarter()
    val unseal = for {
      vaultUnseal <- starter.initializeAndUnsealVault(vaultBaseUrl)
      checkVaultUnsealed <- waitForDNS("vault.service.consul", 15.seconds)
      oauthId = sys.env.get("GOOGLE_OAUTH_ID")
      oauthSecret = sys.env.get("GOOGLE_OAUTH_SECRET")
      registerGoogleOAuthPlugin <- (vaultUnseal, oauthId, oauthSecret) match {
        case (VaultUnsealed(key: String, token: String), Some(a), Some(b)) =>
          starter.initializeGoogleOauthPlugin(token, vaultBaseUrl)
        case (VaultUnsealed(key, token), _, _) =>
          IO(scribe.info(
            "GOOGLE_OAUTH_ID and/or GOOGLE_OAUTH_SECRET are not set. The Google oauth plugin will not be initialized.")) *> IO
            .pure(VaultOauthPluginNotInstalled)
        case (VaultSealed, _, _) =>
          IO(scribe.info(s"Vault remains sealed. Please check your configuration.")) *> IO
            .pure(VaultSealed)
        case (VaultAlreadyUnsealed, _, _) => {
          for {
            _ <- IO.pure("Vault already unsealed")
            unsealToken = sys.env.get("VAULT_TOKEN")
            result <- unsealToken match {
              case Some(token) =>
                starter.initializeGoogleOauthPlugin(token, vaultBaseUrl)
            }
          } yield result
        }
      }
      _ <- IO(scribe.info(s"Plugin Status: $registerGoogleOAuthPlugin"))
      _ <- IO(scribe.info(s"VAULT STATUS: ${vaultUnseal}"))
      vaultToken <- IO(vaultUnseal match {
        case VaultUnsealed(key: String, token: String) => (new VaultUtils).storeVaultTokenKey(key, token)
      })
    } yield vaultToken
    unseal
  }
}
