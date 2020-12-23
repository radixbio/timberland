package com.radix.utils.tls

import java.util.concurrent.TimeoutException

import cats.effect.IO
import cats.effect.concurrent.{MVar, MVar2}
import io.circe.Decoder
import io.circe.parser.decode
import org.http4s.client.dsl.io._
import org.http4s.Method.GET
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Response, Uri}

import SSLContextThreadPool._

import scala.concurrent.duration._

object ConsulConnectSSLContext extends SSLContextBase {
  private lazy val blazeLongPoll = BlazeClientBuilder[IO](executionContext)
    .withCheckEndpointAuthentication(false)
    .withSslContext(ConsulVaultSSLContext.sslContext)
    .withIdleTimeout(90.seconds)
    .resource

  private lazy val rootCerts: MVar2[IO, Seq[RootCert]] = {
    val emptyMVar = MVar.empty[IO, Seq[RootCert]].unsafeRunSync()
    loop(updateRootCert).unsafeRunAsyncAndForget()
    emptyMVar
  }

  private lazy val leafCert: MVar2[IO, LeafCert] = {
    val emptyMVar = MVar.empty[IO, LeafCert].unsafeRunSync()
    loop(updateLeafCert).unsafeRunAsyncAndForget()
    emptyMVar
  }

  private val rootCertIndex: MVar2[IO, Int] = MVar.empty[IO, Int].unsafeRunSync()
  private val leafCertIndex: MVar2[IO, Int] = MVar.empty[IO, Int].unsafeRunSync()

  val serviceName: String = sys.env("HOSTNAME").split("\\.").head

  override def getRootCerts(caPem: Option[String]): Seq[RootCert] = rootCerts.read.unsafeRunSync()
  override def getLeafCert(certPem: Option[String], keyPem: Option[String]): LeafCert = leafCert.read.unsafeRunSync()

  private def loop(fn: () => IO[_]): IO[Nothing] = fn().flatMap(_ => loop(fn))

  private def updateRootCert(): IO[Unit] =
    for {
      indexOption <- rootCertIndex.tryRead
      consulResp <- consulGet[Seq[RootCert]]("v1/agent/connect/ca/roots", indexOption)
      _ <- consulResp match {
        case Left(_: TimeoutException) =>
          scribe.debug(s"timeout while long-polling consul-connect root certificate")
          IO.unit
        case Left(err) =>
          scribe.warn(s"error querying consul connect for root certificates")
          IO.sleep(45.seconds)
        case Right((certs, index)) =>
          scribe.info(s"got new consul connect root certificate, i = $index")
          // Non-atomicity is okay here bc two updateRootCert()s will never run in parallel
          indexOption match {
            case Some(_) => rootCerts.swap(certs) *> rootCertIndex.swap(index)
            case None    => rootCerts.put(certs) *> rootCertIndex.put(index)
          }
      }
    } yield ()

  private def updateLeafCert(): IO[Unit] =
    for {
      indexOption <- leafCertIndex.tryRead
      consulResp <- consulGet[LeafCert](s"v1/agent/connect/ca/leaf/$serviceName", indexOption)
      _ <- consulResp match {
        case Left(_: TimeoutException) =>
          scribe.debug(s"timeout while long-polling consul-connect leaf certificate")
          IO.unit
        case Left(_) =>
          scribe.warn(s"error querying consul connect for leaf certificates")
          IO.sleep(45.seconds)
        case Right((cert, index)) =>
          scribe.info(s"got new consul connect leaf certificate, i = $index")
          // Non-atomicity is okay here bc two updateLeafCert()s will never run in parallel
          indexOption match {
            case Some(_) => leafCert.swap(cert) *> leafCertIndex.swap(index)
            case None    => leafCert.put(cert) *> leafCertIndex.put(index)
          }
      }
    } yield ()

  /**
   *
   * @param path The api path to call
   * @param consulIndex Current index for blocking requests
   * @param decoder A Json decoder from http response to the desired type
   * @tparam T The desired return type
   * @return A tuple containing the response and it's associated X-Consul-Index header
   */
  private def consulGet[T](path: String, consulIndex: Option[Int])(
    implicit decoder: Decoder[T]
  ): IO[Either[Throwable, (T, Int)]] = {
    scribe.debug(s"""asking consul for connect cert at path "$path" where i > ${consulIndex.getOrElse(-1)}""")

    def parseResponse(resp: Response[IO]): IO[(T, Int)] = {
      for {
        respString <- resp.as[String]
        respClass = decode[T](respString).right.get

        consulIndexHeader = resp.headers.get(CaseInsensitiveString("X-Consul-Index"))
        newConsulIndex = consulIndexHeader.map(_.value.toInt).getOrElse(1)
      } yield (respClass, newConsulIndex)
    }

    val queryStr = consulIndex.map("?index=" + _).getOrElse("")
    val consulUri = Uri.unsafeFromString("https://consul.service.consul:8501/" + path + queryStr)
    val accessToken = System.getenv("ACCESS_TOKEN")

    val req = GET(consulUri, Header("X-Consul-Token", accessToken))
    blazeLongPoll.use { client =>
      client.fetch(req)(parseResponse).attempt
    }
  }
}

case class AuthResponse(authorized: Boolean, reason: String)

case object AuthResponse {
  implicit val authResponseDecoder: Decoder[AuthResponse] =
    Decoder.forProduct2("Authorized", "Reason")(AuthResponse.apply)
}
