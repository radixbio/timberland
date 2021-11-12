package com.radix.utils.tls

import javax.net.ssl.SSLContext

trait SSLContextBase {

  def sslContext: SSLContext = SSLContextCreator.getSslContext(getLeafCert(), getRootCerts())

  protected def getRootCerts(caPem: Option[String] = None): Seq[RootCert]
  protected def getLeafCert(certPem: Option[String] = None, keyPem: Option[String] = None): LeafCert
}
