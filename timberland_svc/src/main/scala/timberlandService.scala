package com.radix.timberland_svc.timberlandService

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
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

object timberlandService extends IOApp {
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
      .resource
      .use(_ => IO.never)
      .start
      .flatMap(_.join)

  private def routesWithCORS: Http[IO, IO] = CORS(
    routes.orNotFound,
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set(
        "https://localhost:1337",
        "http://localhost:1337",
        "https://nginx.service.consul:8080",
        "http://nginx.service.consul:8080"
      ),
      allowCredentials = false,
      maxAge = 1.day.toSeconds
    )
  )

  private def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root        => getConfig()
    case req @ POST -> Root => setConfig(req)
    case _ -> Root          => MethodNotAllowed(Allow(GET, POST))
  }

  private def getConfig(): IO[Response[IO]] = ???
  private def setConfig(req: Request[IO]): IO[Response[IO]] = ???

}
