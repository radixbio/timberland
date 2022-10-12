package com.radix.utils.tls
import cats.effect.{ContextShift, IO, Resource, Timer}

import scala.concurrent.ExecutionContext.Implicits.global
import javax.net.ssl._
import akka.actor.ActorSystem
import akka.remote.artery.tcp.SSLEngineProvider
import akka.stream.TLSRole
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.Method.POST
import org.http4s.{Header, Uri}
import com.radix.utils.tls.ConsulVaultSSLContext._

final case class ConsulAuthorizationException(private val message: String) extends Exception(message, null)

class ConsulConnectSSLEngineProvider(system: ActorSystem) extends SSLEngineProvider {
  private implicit val timer: Timer[IO] = IO.timer(global)
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  // https://ciphersuite.info/cs/TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256/
  private val cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"

  override def createServerSSLEngine(hostname: String, port: Int): SSLEngine =
    createSSLEngine(akka.stream.Server, hostname, port)

  override def createClientSSLEngine(hostname: String, port: Int): SSLEngine =
    createSSLEngine(akka.stream.Client, hostname, port)

  private def createSSLEngine(role: TLSRole, hostname: String, port: Int): SSLEngine = {
    val engine = ConsulConnectSSLContext.sslContext.createSSLEngine(hostname, port)

    engine.setUseClientMode(role == akka.stream.Client)
    engine.setEnabledCipherSuites(Array(cipherSuite))
    engine.setEnabledProtocols(Array("TLSv1.2"))

    if (role != akka.stream.Client)
      engine.setNeedClientAuth(true)

    engine
  }

  override def verifyClientSession(hostname: String, session: SSLSession): Option[Throwable] =
    validateConnection(hostname, session)

  override def verifyServerSession(hostname: String, session: SSLSession): Option[Throwable] =
    validateConnection(hostname, session)

  private def validateConnection(hostname: String, session: SSLSession): Option[Throwable] = {
    import org.http4s.circe._
    import org.http4s.client.dsl.io._

    val req = POST(
      Map(
        "Target" -> hostname,
        "ClientCertURI" -> ConsulConnectSSLContext.getLeafCert().uri,
        "ClientCertSerial" -> ConsulConnectSSLContext.getLeafCert().serial,
      ).asJson,
      Uri.unsafeFromString("https://consul.service.consul:8501/v1/agent/connect/authorize"),
      Header("X-Consul-Token", System.getenv("ACCESS_TOKEN")),
    )
    val resp = blaze
      .use { client =>
        client.expect[String](req)
      }
      .map(decode[AuthResponse](_))
      .map(_.right.get)
      .unsafeRunSync()
    if (!resp.authorized) {
      scribe.warn(s"unauthorized consul-connect native connection!")
      scribe.warn(resp.reason)
      Some(ConsulAuthorizationException(resp.reason))
    } else None
  }
}
