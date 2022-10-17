package com.radix.utils.tls

import java.io.{ByteArrayInputStream, StringReader}
import java.security.cert.{Certificate, CertificateFactory}
import java.security.{KeyStore, KeyStoreException, PrivateKey, SecureRandom, Security}

import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManager, TrustManagerFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

case class LeafCert(serviceName: String, cert: Certificate, privateKey: PrivateKey, uri: String, serial: String)
case class RootCert(id: String, cert: Certificate)

object SSLContextCreator {
  def getSslContext(implicit leafCert: LeafCert, rootCerts: Seq[RootCert]): SSLContext = {
    val rng = new SecureRandom
    rng.nextInt()
    val ctx = SSLContext.getInstance("TLSv1.2")
    ctx.init(getKeyManagers, getTrustStore, rng)
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

  private def getTrustStore(implicit rootCerts: Seq[RootCert]): Array[TrustManager] = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null)
    for (rootCert <- rootCerts) {
      keyStore.setCertificateEntry(rootCert.id, rootCert.cert)
    }

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(keyStore)
    trustManagerFactory.getTrustManagers
  }
}
