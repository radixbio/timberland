package com.radix.utils.tls

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.server.ServerRequestKeys
import org.http4s.{Header, HttpRoutes, Request, Response, Uri}
import io.circe.syntax._
import io.circe.parser.decode
import org.http4s.client.dsl.io._
import org.http4s.Method.POST
import org.http4s.circe._
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import org.http4s.CharsetRange.*

/**
 * This object contains blaze middleware designed to intercept incoming mTLS requests and verify their
 * client certificates against the consul connect native intent authorization api before proceeding
 */
case object ConnectNativeBlazeMiddleware {
  def middleware(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli[OptionT[IO, *], Request[IO], Response[IO]] {
    (req: Request[IO]) =>
      val reqIO: Option[IO[Request[IO]]] =
        req.attributes.lookup(ServerRequestKeys.SecureSession).flatten.map { session =>
          val cert = session.X509Certificate.head
          val serialByteString = cert.getSerialNumber.toString(16).reverse.padTo(22, "0").reverse
          val byteStringWithColons = serialByteString.grouped(2).map(_.mkString).mkString(":")
          val authRequestPayload = Map(
            "Target" -> req.serverAddr,
            "ClientCertURI" -> "",
            "ClientCertSerial" -> byteStringWithColons,
          ).asJson
          POST(
            authRequestPayload,
            Uri.unsafeFromString("https://consul.service.consul:8501/v1/agent/connect/authorize"),
            Header("X-Consul-Token", System.getenv("ACCESS_TOKEN")),
          )
        }

      val reqOption: OptionT[IO, Request[IO]] = OptionT(reqIO match {
        case Some(x) => x.map(Some(_))
        case None    => IO.pure(None)
      })

      val resp = (consulReq: Request[IO]) =>
        OptionT(
          blaze
            .use { client =>
              client.expect[String](consulReq)
            }
            .map(decode[AuthResponse](_))
            .map(_.toOption)
        )

      for {
        consulReq <- reqOption
        consulResp <- resp(consulReq)
        realResp <- consulResp match {
          case AuthResponse(true, _) => service(req)
          case _                     => OptionT.none[IO, Response[IO]]
        }
      } yield realResp
  }
}
