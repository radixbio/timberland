package com.radix.utils.tls

import cats.effect.{ContextShift, IO, Resource, Timer}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.blaze.util.TickWheelExecutor

import scala.concurrent.ExecutionContext.Implicits.global

object ConsulVaultSSLContext {
  private implicit val timer: Timer[IO] = IO.timer(global)
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
  private implicit val scheduler: TickWheelExecutor = new TickWheelExecutor()
  private val certsPath = os.root / "opt" / "radix" / "certs"
  private val caPath = sys.env.get("TLS_CA").map(os.Path(_)).getOrElse(certsPath / "ca" / "cert.pem")
  private val certPath = sys.env.get("TLS_CERT").map(os.Path(_)).getOrElse(certsPath / "cli" / "cert.pem")
  private val keyPath = sys.env.get("TLS_KEY").map(os.Path(_)).getOrElse(certsPath / "cli" / "key.pem")

  implicit def blaze: Resource[IO, Client[IO]] = blazeOption.getOrElse {
    refreshCerts()
    blazeOption.get
  }

  private var blazeOption: Option[Resource[IO, Client[IO]]] = None
  def refreshCerts(
    caPem: Option[String] = None,
    certPem: Option[String] = None,
    keyPem: Option[String] = None
  ): Unit = {
    val ca = getCA(caPem).unsafeRunSync()
    val certAndKey = getCertAndKey(certPem, keyPem).unsafeRunSync()
    val sslContext = SSLContextCreator.getSslContext(certAndKey, ca)
    val newBlaze = BlazeClientBuilder[IO](global)
      .withCheckEndpointAuthentication(false)
      .withSslContext(sslContext)
      .withScheduler(scheduler)
      .resource
    blazeOption = Some(newBlaze)
  }

  private def getCA(caPem: Option[String]): IO[Array[RootCert]] =
    for {
      caCertPem <- caPem.map(IO.pure).getOrElse(IO(os.read(caPath)))
    } yield {
      val cert = SSLContextCreator.certFromPemString(caCertPem)
      Array(RootCert("Vault CA", cert))
    }

  private def getCertAndKey(certPem: Option[String], keyPem: Option[String]): IO[LeafCert] =
    for {
      certPem <- certPem.map(IO.pure).getOrElse(IO(os.read(certPath)))
      keyPem <- keyPem.map(IO.pure).getOrElse(IO(os.read(keyPath)))
    } yield {
      val cert = SSLContextCreator.certFromPemString(certPem)
      val key = SSLContextCreator.keyFromPemString(keyPem)
      LeafCert("Vault TLS", cert, key, "", "")
    }
}
