package com.radix.timberland_svc.timberlandService

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
import com.radix.timberland.{launch, ConstPaths}
import com.radix.timberland.flags.{configGen, featureFlags}
import com.radix.timberland.launch.daemonutil
import com.radix.timberland.radixdefs.ServiceAddrs
import com.radix.timberland.runtime._
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.all._

object timberlandService extends IOApp {

  val serviceController = Services.serviceController

  override def run(args: List[String]): IO[Nothing] =
    for {
      args <- auth.recoverArgs
      context <- auth.recoverRunContext(args)
      addrs = context._1
      tokens = context._2
      _ <- IO.race(reloadTemplateLoop(tokens, args.datacenter), startTimberlandConfigServer(addrs, tokens))
      never <- IO.never
    } yield never

  private def reloadTemplateLoop(tokens: AuthTokens, datacenter: String): IO[Nothing] =
    for {
      _ <- IO.sleep(1.hour)
      _ <- launch.dns.up() // Ensure DNS entry exists
      _ <- serviceController.runConsulTemplate(tokens.consulNomadToken, tokens.vaultToken, None, datacenter)
      nothing <- reloadTemplateLoop(tokens, datacenter)
    } yield nothing

  private def startTimberlandConfigServer(implicit addrs: ServiceAddrs, auth: AuthTokens): IO[Nothing] =
    BlazeServerBuilder[IO]
      .bindHttp(7777, "localhost")
      .withHttpApp(routesWithCORS)
      .resource
      .use(_ => IO.never)
      .start
      .flatMap(_.join)

  private def routesWithCORS(implicit addrs: ServiceAddrs, auth: AuthTokens): Http[IO, IO] = CORS(
    routes.orNotFound,
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set(
        "https://localhost:1337",
        "http://localhost:1337",
        "https://nginx.service.consul:8080",
        "http://nginx.service.consul:8080",
      ),
      allowCredentials = false,
      maxAge = 1.day.toSeconds,
    ),
  )

  private def routes(implicit addrs: ServiceAddrs, auth: AuthTokens): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "config" / module        => getConfig(module)
    case req @ POST -> Root / "config" / module => setConfig(module, req)
    case GET -> Root / "flags"                  => getFlags
    case req @ POST -> Root / "flags"           => setFlags(req)
    case POST -> Root / "run"                   => featureFlags.runHooks *> daemonutil.runTerraform() *> Ok() // TODO: Add dc
    case _ -> Root                              => NotFound()
  }

  private def getConfig(module: String): IO[Response[IO]] = configGen.getConfig(module).flatMap(cfg => Ok(cfg.asJson))
  private def setConfig(module: String, req: Request[IO]): IO[Response[IO]] = for {
    configChanges <- req.as[Map[String, Json]]
    oldConfig <- configGen.getConfig(module)
    newConfig = oldConfig ++ configChanges
    newJson = Map(s"config_$module" -> newConfig)
    configFile = ConstPaths.TF_CONFIG_DIR / s"$module.json"
    _ <- IO(os.write.over(configFile, newJson.asJson.toString()))
    ok <- Ok()
  } yield ok

  private def getFlags: IO[Response[IO]] = featureFlags.flags.flatMap(flags => Ok(flags.asJson))
  private def setFlags(req: Request[IO]): IO[Response[IO]] = for {
    flagChanges <- req.as[Map[String, Boolean]]
    oldFlagMap <- featureFlags.flags
    newFlagMap = oldFlagMap ++ flagChanges
    newJson = Map("feature_flags" -> newFlagMap)
    _ <- IO(os.write.over(featureFlags.FLAGS_JSON, newJson.asJson.toString()))
    ok <- Ok()
  } yield ok

}
