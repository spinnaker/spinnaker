/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.webhook.util

import spock.lang.Specification
import spock.lang.Unroll

import javax.net.ssl.X509TrustManager
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class UnionX509TrustManagerSpec extends Specification {
  static TRUST_ALL = new X509TrustManager() {
    @Override
    void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0]
    }
  }

  static TRUST_NONE = new X509TrustManager() {
    @Override
    void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      throw new CertificateException("Nothing is trusted")
    }

    @Override
    void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      throw new CertificateException("Nothing is trusted")
    }

    @Override
    X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0]
    }
  }

  @Unroll
  def "checkClientTrusted passes if at least one delegate passes"() {
    given:
    UnionX509TrustManager trustManager

    when:
    trustManager = new UnionX509TrustManager(delegates)
    trustManager.checkClientTrusted([] as X509Certificate[], "test")

    then:
    noExceptionThrown()

    where:
    delegates               | _
    [TRUST_ALL]             | _
    [TRUST_ALL, TRUST_NONE] | _
    [TRUST_NONE, TRUST_ALL] | _
    [TRUST_ALL, TRUST_ALL]  | _
  }

  @Unroll
  def "checkClientTrusted fails if at least one delegate passes"() {
    given:
    UnionX509TrustManager trustManager

    when:
    trustManager = new UnionX509TrustManager(delegates)
    trustManager.checkClientTrusted([] as X509Certificate[], "test")

    then:
    thrown(CertificateException)

    where:
    delegates                | _
    []                       | _
    [TRUST_NONE]             | _
    [TRUST_NONE, TRUST_NONE] | _
  }

  @Unroll
  def "checkServerTrusted passes if at least one delegate passes"() {
    given:
    UnionX509TrustManager trustManager

    when:
    trustManager = new UnionX509TrustManager(delegates)
    trustManager.checkServerTrusted([] as X509Certificate[], "test")

    then:
    noExceptionThrown()

    where:
    delegates               | _
    [TRUST_ALL]             | _
    [TRUST_ALL, TRUST_NONE] | _
    [TRUST_NONE, TRUST_ALL] | _
    [TRUST_ALL, TRUST_ALL]  | _
  }

  @Unroll
  def "checkServerTrusted fails if at least one delegate passes"() {
    given:
    UnionX509TrustManager trustManager

    when:
    trustManager = new UnionX509TrustManager(delegates)
    trustManager.checkServerTrusted([] as X509Certificate[], "test")

    then:
    thrown(CertificateException)

    where:
    delegates                | _
    []                       | _
    [TRUST_NONE]             | _
    [TRUST_NONE, TRUST_NONE] | _
  }
}
