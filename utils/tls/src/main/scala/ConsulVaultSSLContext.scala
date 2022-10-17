package com.radix.utils.tls

import cats.effect.{ConcurrentEffect, Effect, IO, Resource}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import SSLContextThreadPool._

object ConsulVaultSSLContext extends SSLContextBase {
  private val certsPath = os.root / "opt" / "radix" / "certs"
  private val caPath = sys.env.get("TLS_CA").map(os.Path(_)).getOrElse(certsPath / "ca" / "cert.pem")
  private val certPath = sys.env.get("TLS_CERT").map(os.Path(_)).getOrElse(certsPath / "cli" / "cert.pem")
  private val keyPath = sys.env.get("TLS_KEY").map(os.Path(_)).getOrElse(certsPath / "cli" / "key.pem")

  implicit def blaze: Resource[IO, Client[IO]] = blazeOption.getOrElse {
    refreshCerts()
    blazeOption.get
  }

  implicit def Fblaze[F[_]: ConcurrentEffect]: Resource[F, Client[F]] = {
    var fBlazeOpt: Option[Resource[F, Client[F]]] = None

    fBlazeOpt.getOrElse({
      fBlazeOpt = Some(makeBlaze[F](None, None, None))
      fBlazeOpt.get
    })
  }



  protected var blazeOption: Option[Resource[IO, Client[IO]]] = None
  def refreshCerts(
    caPem: Option[String] = None,
    certPem: Option[String] = None,
    keyPem: Option[String] = None
  ): Unit = {
    blazeOption = Some(makeBlaze[IO](caPem, certPem, keyPem))
  }

  def makeBlaze[F[_]](
    caPem: Option[String] = None,
    certPem: Option[String] = None,
    keyPem: Option[String] = None
  )(implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = {
    val roots = getRootCerts(caPem)
    val leaf = getLeafCert(certPem, keyPem)
    val sslContext = SSLContextCreator.getSslContext(leaf, roots)
    BlazeClientBuilder[F](executionContext)
      .withCheckEndpointAuthentication(false)
      .withSslContext(sslContext)
      .resource
  }

  override def getRootCerts(caPem: Option[String]): Seq[RootCert] = {
    val caCertPem = caPem.getOrElse(os.read(caPath))
    val cert = SSLContextCreator.certFromPemString(caCertPem)
    Seq(RootCert("Vault CA", cert))
  }

  override def getLeafCert(certPem: Option[String], keyPem: Option[String]): LeafCert = {
    val actualCertPem = certPem.getOrElse(os.read(certPath))
    val actualKeyPem = keyPem.getOrElse(os.read(keyPath))
    val cert = SSLContextCreator.certFromPemString(actualCertPem)
    val key = SSLContextCreator.keyFromPemString(actualKeyPem)
    LeafCert("Vault TLS", cert, key, "", "")
  }
}