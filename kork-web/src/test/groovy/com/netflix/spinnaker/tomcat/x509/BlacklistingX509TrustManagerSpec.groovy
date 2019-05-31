/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.tomcat.x509

import com.netflix.spectator.api.NoopRegistry
import spock.lang.Specification

import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import java.security.cert.CertificateRevokedException
import java.security.cert.X509Certificate

class BlacklistingX509TrustManagerSpec extends Specification {
  def delegate = Mock(X509TrustManager)
  def blacklist = Mock(Blacklist)

  def trustManager = new BlacklistingX509TrustManager(delegate, blacklist, new NoopRegistry())

  def "should delegate for server auth"() {
    when:
    trustManager.checkServerTrusted(certs, authType)

    then:
    1 * delegate.checkServerTrusted(certs, authType)
    0 * _

    where:
    cert = testCert(12345, "Test")
    certs = [cert] as X509Certificate[]
    authType = 'RSA'
  }

  def "should delegate for trusted issuers"() {
    when:
    trustManager.getAcceptedIssuers()

    then:
    1 * delegate.getAcceptedIssuers() >> []
    0 * _
  }

  def "should fall back to delegate if not blacklisted"() {

    when:
    trustManager.checkClientTrusted(certs, authType)

    then:
    1 * blacklist.isBlacklisted(cert) >> false
    1 * delegate.checkClientTrusted(certs, authType)
    0 * _

    where:
    cert = testCert(12345, "Test")
    certs = [cert] as X509Certificate[]
    authType = 'RSA'
  }

  def "should reject a blacklisted cert"() {
    when:
    trustManager.checkClientTrusted(certs, authType)

    then:
    1 * blacklist.isBlacklisted(cert) >> true
    thrown(CertificateRevokedException)
    0 * _


    where:
    cert = testCert(12345, "Test")
    certs = [cert] as X509Certificate[]
    authType = 'RSA'
  }

  private X509Certificate testCert(int certSerial, String issuerCN) {
    BigInteger serial = certSerial as BigInteger
    X500Principal issuer = new X500Principal("O=Test, OU=Test, CN=$issuerCN, L=Testland, ST=CA, C=US")


    return Stub(X509Certificate) {
      getSerialNumber() >> serial
      getIssuerX500Principal() >> issuer
    }

  }
}
