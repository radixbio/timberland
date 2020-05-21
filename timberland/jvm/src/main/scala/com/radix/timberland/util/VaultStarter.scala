package com.radix.timberland.launch

import cats.effect.{ContextShift, IO, Timer}
import com.radix.timberland.launch.daemonutil.timeout
import com.radix.utils.helm.vault.{CreateSecretRequest, EnableSecretsEngine, InitRequest, RegisterPluginRequest, Secret, UnsealRequest}
import org.http4s.implicits._
import org.http4s.client.blaze.BlazeClientBuilder
import com.radix.utils.helm.http4s.vault.{Vault => VaultSession}
import com.radix.utils.helm.http4s.Http4sNomadClient
import com.radix.utils.helm.vault._
import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.Uri.uri

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

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

class VaultStarter {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  /**
   * We can not use consul to look up the IP of Vault via DNS because Vault installs a consul service check
   * which only passes if the Vault is initialized. On first run the Vault is not initialized, therefore the
   * service check fails, therefore there is no A record returned.
   *
   * To solve this, we ask Nomad what the IP address is.
   */
  def lookupVaultBaseUrl(): IO[Uri] = {
    BlazeClientBuilder[IO](global).resource
      .use(implicit client => {
        implicit val interp: Http4sNomadClient[IO] =
          new Http4sNomadClient[IO](uri"http://nomad.service.consul:4646", client)
        for {
          listResponse <- interp.nomadListAllocations()
          allocationMap = listResponse.allocations.map { item => (item.jobId, item.id) }.toMap
          vaultAllocationId = allocationMap("vault-daemon")
          vaultAllocation <- interp.nomadDescribeAllocation(vaultAllocationId)
          vaultNetwork = vaultAllocation.networks(0) // TODO Dangerous
          vaultIp = vaultNetwork.ip
          vaultPort = vaultNetwork.reservedPorts.find(_.label == "vault_listen").flatMap { p => Some(p.port) }.getOrElse(8200)
        } yield Uri.fromString(s"http://$vaultIp:$vaultPort").getOrElse(uri"http://vault-daemon-vault-vault.service.consul:8200")
      })
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

  def waitOnVaultInit(
                       implicit vaultSession: VaultSession[IO]): IO[VaultInitStatus] = {
    for {
      _ <- IO.sleep(1.second)
      initStatus <- checkVaultInitStatus
      vaultInitStatus <- initStatus match { //TODO: NEED TO ADD A MATCH STATEMENT HERE
        case Right(false) => {
          val initReq = InitRequest(1, 1)
          for {
            vaultResponse <- vaultSession.sysInit(initReq)
            initStatus <- vaultResponse match {
              case Right(status) => {
                IO(scribe.trace(s"******** VAULT KEY and TOKEN: ${status
                  .keys(0)}, TOKEN: ${status.root_token}")) *> IO.pure(
                  VaultInitialized(status.keys(0), status.root_token))
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

  //TODO plugin initialization should take place in the oneshot job
  def initializeGoogleOauthPlugin(token: String): IO[VaultOAuthStatus] = {
    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.trace("Registering Google Plugin...")

      implicit val vaultSession: VaultSession[IO] =
        new VaultSession[IO](authToken = Some(token),
          baseUrl =
            uri"http://vault-daemon.service.consul:8200",
          blazeClient = client)

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

  def unsealVault(dev: Boolean, baseUrl: Uri): IO[VaultSealStatus] = {
    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.trace("Checking Vault Status...")

      scribe.info(s"VAULT BASE URL: $baseUrl")

      implicit val vaultSession: VaultSession[IO] =
        new VaultSession[IO](baseUrl = baseUrl, blazeClient = client, authToken = None)

      val result = for {
        vaultInit <- waitOnVaultInit
        finalState <- vaultInit match {
          case VaultNotInitalized => IO.pure(VaultSealed)
          case VaultInitialized(key, root_token) =>
            waitOnVaultUnseal(key, root_token)
          case VaultAlreadyInitialized => {
            for {
              sealStatus <- checkVaultSealStatus
              seal <- sealStatus match {
                case Right(status) =>
                  status.`sealed` match {
                    case false => IO.pure(VaultAlreadyUnsealed)
                    case true  => IO.pure(VaultSealed)
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
          timeout(
            unsealVault(dev, baseUrl),
            new FiniteDuration(10, duration.MINUTES)
          )
        case Right(finalState) => IO.pure(finalState)
      }

    })
  }
}
