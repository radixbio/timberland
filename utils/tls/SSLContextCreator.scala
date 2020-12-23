package com.radix.utils.tls

import java.io.{ByteArrayInputStream, StringReader}
import java.security.cert.{Certificate, CertificateException, CertificateFactory, X509Certificate}
import java.security.{KeyStore, KeyStoreException, PrivateKey, SecureRandom, Security}
import java.util.concurrent.Executors

import javax.net.ssl.X509TrustManager
import cats.effect.{ContextShift, IO, Timer}
import io.circe.{Decoder, HCursor}
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

case class LeafCert(serviceName: String, cert: Certificate, privateKey: PrivateKey, uri: String, serial: String)
object LeafCert {
  implicit val leafCertDecoder: Decoder[LeafCert] = (c: HCursor) =>
    for {
      serviceName <- c.downField("Service").as[String]
      certPem <- c.downField("CertPEM").as[String]
      privateKeyPem <- c.downField("PrivateKeyPEM").as[String]
      uri <- c.downField("ServiceURI").as[String]
      serial <- c.downField("SerialNumber").as[String]
    } yield {
      val cert = SSLContextCreator.certFromPemString(certPem)
      val key = SSLContextCreator.keyFromPemString(privateKeyPem)
      LeafCert(serviceName, cert, key, uri, serial)
    }
}

case class RootCert(id: String, cert: Certificate)
object RootCert {
  implicit val rootCertDecoder: Decoder[RootCert] = Decoder.instance { root =>
    for {
      id <- root.downField("ID").as[String]
      certPem <- root.downField("RootCert").as[String]
    } yield {
      val cert = SSLContextCreator.certFromPemString(certPem)
      RootCert(id, cert)
    }
  }
  implicit val rootCertsDecoder: Decoder[Seq[RootCert]] = Decoder.instance { cursor =>
    cursor.get("Roots")(Decoder.decodeSeq[RootCert])
  }
}

object SSLContextThreadPool {
  implicit val executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)
}

object SSLContextCreator {
  def getSslContext(implicit leafCert: LeafCert, rootCerts: Seq[RootCert]): SSLContext = {
    val rng = new SecureRandom
    rng.nextInt()
    val ctx = SSLContext.getInstance("TLSv1.2")
    ctx.init(getKeyManagers, Array(getTrustStore), rng)
    ctx
  }

  def certFromPemString(pemString: String): Certificate = {
    val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
    val inputStream = new ByteArrayInputStream(pemString.getBytes("UTF-8"))
    val cert = certFactory.generateCertificate(inputStream)
    inputStream.close()
    cert
  }

  def keyFromPemString(pemString: String): PrivateKey = {
    Security.addProvider(new BouncyCastleProvider())
    val pemStrReader = new StringReader(pemString)
    val pemKeyPair = new PEMParser(pemStrReader).readObject.asInstanceOf[PEMKeyPair]
    val javaKeyPair = new JcaPEMKeyConverter().getKeyPair(pemKeyPair)
    javaKeyPair.getPrivate
  }

  private def getKeyManagers(implicit leafCert: LeafCert, rootCerts: Seq[RootCert]): Array[KeyManager] = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null)
    for (rootCert <- rootCerts) try {
      val chain = Array(leafCert.cert, rootCert.cert)
      keyStore.setKeyEntry(leafCert.serviceName, leafCert.privateKey, Array.emptyCharArray, chain)
    } catch {
      case _: KeyStoreException => ()
    }

    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, Array.emptyCharArray)
    factory.getKeyManagers
  }

  private def getTrustStore(implicit rootCerts: Seq[RootCert]): X509TrustManager = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null)
    for (rootCert <- rootCerts) {
      keyStore.setCertificateEntry(rootCert.id, rootCert.cert)
    }

    val customTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    customTrustManagerFactory.init(keyStore)
    val customTrustManager = getX509TrustManager(customTrustManagerFactory)

    val defaultTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    defaultTrustManagerFactory.init(null.asInstanceOf[KeyStore])
    val defaultTrustManager = getX509TrustManager(defaultTrustManagerFactory)

    mergeTrustManagers(defaultTrustManager, customTrustManager)
  }

  private def getX509TrustManager(trustManagerFactory: TrustManagerFactory): X509TrustManager =
    trustManagerFactory.getTrustManagers.find(_.isInstanceOf[X509TrustManager]).get.asInstanceOf[X509TrustManager]

  private def mergeTrustManagers(defaultTm: X509TrustManager, customTm: X509TrustManager): X509TrustManager =
    new X509TrustManager() {
      override def getAcceptedIssuers: Array[X509Certificate] =
        customTm.getAcceptedIssuers ++ defaultTm.getAcceptedIssuers

      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit =
        try customTm.checkServerTrusted(chain, authType)
        catch { case _: CertificateException => defaultTm.checkServerTrusted(chain, authType) }

      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit =
        try customTm.checkClientTrusted(chain, authType)
        catch { case _: CertificateException => defaultTm.checkClientTrusted(chain, authType) }
    }
}
