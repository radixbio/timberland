package com.radix.timberland_svc.timberlandService

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
import com.radix.timberland.flags.config.flagConfigParams
import com.radix.timberland.flags.featureFlags.{callNonpersistentFlagHooks, setConsulFlags}
import com.radix.timberland.flags.flagConfig.{addMissingParams, readFlagConfig, writeFlagConfig}
import com.radix.timberland.flags.{FlagConfigEntry, FlagConfigs, featureFlags}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import com.radix.timberland.util.RadPath
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.headers._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.all._

case class TimberlandFlag(
  name: String,
  enabled: Boolean,
  config: List[FlagConfigEntry]
)

case class TimberlandFlagUpdate(flagName: String, enable: Boolean, config: Option[Map[String, String]])

object timberlandService extends IOApp {
  import Decoders._
  implicit val serviceAddrs: ServiceAddrs = ServiceAddrs()

  val serviceController = Services.serviceController

  override def run(args: List[String]): IO[Nothing] = {
    IO.race(reloadTemplateLoop(), startTimberlandConfigServer()).flatMap(_ => IO.never)
  }

  private def reloadTemplateLoop(): IO[Nothing] =
    for {
      _ <- IO.sleep(1.hour)
      _ <- serviceController.restartConsulTemplate()
      nothing <- reloadTemplateLoop()
    } yield nothing

  private def startTimberlandConfigServer(): IO[Nothing] =
    BlazeServerBuilder[IO]
      .bindHttp(7777, "localhost")
      .withHttpApp(routesWithCORS)
      .resource.use(_ => IO.never)
      .start
      .flatMap(_.join)

  private def routesWithCORS: Http[IO, IO] = CORS(routes.orNotFound, CORSConfig(
    anyOrigin = false,
    allowedOrigins = Set("https://localhost:1337", "http://localhost:1337"),
    allowCredentials = false,
    maxAge = 1.day.toSeconds))

  private def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root => getConfig()
    case req @ POST -> Root => setConfig(req)
    case _ -> Root => MethodNotAllowed(Allow(GET, POST))
  }

  private def getConfig(): IO[Response[IO]] =
    for {
      authTokens <- auth.getAuthTokens(isRemote = false, ServiceAddrs(), None, None)
      validFlags <- featureFlags.getValidFlags(RadPath.persistentDir, Some(authTokens))
      flagValues <- featureFlags.getConsulFlags(ServiceAddrs(), authTokens)

      responseObj = validFlags.toSeq.map { flagName => TimberlandFlag(
        name = flagName,
        enabled = flagValues.getOrElse(flagName, false),
        config = flagConfigParams.getOrElse(flagName, List())
      )}
      response <- Ok(responseObj.asJson)
    } yield response

  private def setConfig(req: Request[IO]): IO[Response[IO]] =
    for {
      updatePayload <- req.as[TimberlandFlagUpdate]
      authTokens <- auth.getAuthTokens(isRemote = false, ServiceAddrs(), None, None)
      flagMap = Map(updatePayload.flagName -> updatePayload.enable)
      _ <- setConsulFlags(flagMap)(ServiceAddrs(), authTokens)
      originalFlagConfig <- readFlagConfig(ServiceAddrs(), RadPath.persistentDir, Some(authTokens))
      config = Some(updatePayload.config.getOrElse(Map.empty))
      clearedOutFlagConfig = FlagConfigs(
        originalFlagConfig.consulData + (updatePayload.flagName -> Map.empty),
        originalFlagConfig.vaultData + (updatePayload.flagName -> Map.empty)
      )
      newFlagConfig <- addMissingParams(List(updatePayload.flagName), clearedOutFlagConfig, config)
      _ <- writeFlagConfig(newFlagConfig)(ServiceAddrs(), RadPath.persistentDir, Some(authTokens))
      _ <- callNonpersistentFlagHooks(flagMap, config)
      _ <- daemonutil.runTerraform(flagMap)(ServiceAddrs(), authTokens)
      ok <- Ok()
    } yield ok

}

object Decoders {
  implicit val timberlandFlagConfigEncoder: Encoder[FlagConfigEntry] =
    Encoder.forProduct4("name", "description", "optional", "default") { obj =>
      (obj.key, obj.prompt, obj.optional, obj.default)
    }
  implicit val timberlandFlagEncoder: Encoder[TimberlandFlag] = deriveEncoder
  implicit val timberlandFlagUpdateDecoder: Decoder[TimberlandFlagUpdate] = deriveDecoder
}
