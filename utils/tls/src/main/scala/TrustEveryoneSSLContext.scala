package com.radix.utils.tls

import java.security.cert.X509Certificate

import cats.effect.{ContextShift, IO, Resource}
import javax.net.ssl.{HostnameVerifier, SSLContext, SSLSession, X509TrustManager}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object TrustAll extends X509TrustManager {
  val getAcceptedIssuers = null

  def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = {}

  def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = {}
}

// Verifies all host names by simply returning true.
object VerifiesAllHostNames extends HostnameVerifier {
  def verify(s: String, sslSession: SSLSession) = true
}

object TrustEveryoneSSLContext {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(global)

  val sslContext: SSLContext = SSLContext.getInstance("SSL")
  sslContext.init(null, Array(TrustAll), new java.security.SecureRandom())

  val insecureBlaze: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](global)
    .withCheckEndpointAuthentication(false)
    .withSslContext(sslContext)
    .resource
}
