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

import spock.lang.Specification

import javax.security.auth.x500.X500Principal
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class ReloadingFileBlacklistSpec extends Specification {
  def "should extract cert identification from blacklist file"() {
    given:
    def blFile = createBlacklist()
    def bl = new ReloadingFileBlacklist(blFile.getPath())

    expect:
    bl.isBlacklisted(cert) == expected

    where:
    issuerSource | serial | expected
    "O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US" | 1 | true
    "O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US" | 2 | true
    "O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US" | 2 | true
    "O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US" | 4 | false
    "O=Test, OU=Test, CN=TestCN2, L=Testland, ST=CA, C=US" | 1 | true
    "O=Test, OU=Test, CN=TestCN2, L=Testland, ST=CA, C=US" | 2 | false

    cert = testCert(serial, issuerSource)
  }

  def "should reload cert identification from blacklist file"() {
    given:
    def blFile = createBlacklist()
    def bl = new ReloadingFileBlacklist(blFile.getPath(), 1, TimeUnit.MILLISECONDS)

    when:
    def result = bl.isBlacklisted(cert)

    then:
    result == false

    when:
    blFile.text += issuerSource + ':::' + serial
    Thread.sleep(10)

    and:
    result = bl.isBlacklisted(cert)

    then:
    result == true


    where:
    issuerSource = "O=Test, OU=Test, CN=TestCN2, L=Testland, ST=CA, C=US"
    serial = 2
    cert = testCert(serial, issuerSource)
  }



  private File createBlacklist() {
    def bl = File.createTempFile("blacklist", ".test")
    bl.deleteOnExit()

    bl.text = '''\
    # a comment

    O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US:::1
    O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US:::2
    O=Test, OU=Test, CN=TestCN, L=Testland, ST=CA, C=US:::3
    O=Test, OU=Test, CN=TestCN2, L=Testland, ST=CA, C=US:::1
    '''.stripIndent()

    return bl
  }

  private X509Certificate testCert(int certSerial, String issuerName) {
    BigInteger serial = certSerial as BigInteger
    X500Principal issuer = new X500Principal(issuerName)

    return Stub(X509Certificate) {
      getSerialNumber() >> serial
      getIssuerX500Principal() >> issuer
    }
  }
}
