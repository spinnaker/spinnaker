/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.SslCertificate
import com.google.api.services.compute.model.SslCertificateList
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SSL_CERTIFICATES

class GoogleSslCertificateCachingAgentSpec extends Specification {
  private static final String PROJECT = "my-project"
  private static final String ACCOUNT = "auto"
  private static final String REGION = "us-central1"

  void "regional agent reads regional certificates and keys them by region"() {
    given:
    def compute = Mock(Compute)
    def regionSslCertificates = Mock(Compute.RegionSslCertificates)
    def regionSslCertificatesList = Mock(Compute.RegionSslCertificates.List)
    @Subject def agent = createAgent(compute, REGION)

    when:
    def cacheResult = agent.loadData(Mock(ProviderCache))

    then:
    1 * compute.regionSslCertificates() >> regionSslCertificates
    1 * regionSslCertificates.list(PROJECT, REGION) >> regionSslCertificatesList
    1 * regionSslCertificatesList.execute() >> new SslCertificateList(items: [new SslCertificate(name: "regional-cert")])
    0 * compute.sslCertificates()

    def cacheData = cacheResult.cacheResults.get(SSL_CERTIFICATES.ns)
    cacheData.size() == 1
    cacheData[0].id == Keys.getSslCertificateKey(ACCOUNT, REGION, "regional-cert")
    cacheData[0].attributes.name == "regional-cert"
    cacheData[0].attributes.region == REGION
  }

  void "global agent reads global certificates and keys them without a region"() {
    given:
    def compute = Mock(Compute)
    def sslCertificates = Mock(Compute.SslCertificates)
    def sslCertificatesList = Mock(Compute.SslCertificates.List)
    @Subject def agent = createAgent(compute, null)

    when:
    def cacheResult = agent.loadData(Mock(ProviderCache))

    then:
    1 * compute.sslCertificates() >> sslCertificates
    1 * sslCertificates.list(PROJECT) >> sslCertificatesList
    1 * sslCertificatesList.execute() >> new SslCertificateList(items: [new SslCertificate(name: "global-cert")])
    0 * compute.regionSslCertificates()

    def cacheData = cacheResult.cacheResults.get(SSL_CERTIFICATES.ns)
    cacheData.size() == 1
    cacheData[0].id == Keys.getSslCertificateKey(ACCOUNT, "global-cert")
    cacheData[0].attributes.name == "global-cert"
    !cacheData[0].attributes.containsKey("region")
  }

  void "the keys each agent writes parse back to the scope they were written for"() {
    expect:
    with(Keys.parse(Keys.getSslCertificateKey(ACCOUNT, REGION, "regional-cert"))) {
      it.type == SSL_CERTIFICATES.ns
      it.account == ACCOUNT
      it.region == REGION
      it.name == "regional-cert"
    }

    and: "a global key predates the regional format and must keep parsing without a region"
    with(Keys.parse(Keys.getSslCertificateKey(ACCOUNT, "global-cert"))) {
      it.type == SSL_CERTIFICATES.ns
      it.account == ACCOUNT
      it.region == null
      it.name == "global-cert"
    }
  }

  void "agent type separates the regional agents from the global agent"() {
    given:
    def compute = Mock(Compute)

    expect: "cats scopes authoritative eviction per agent type, so a global refresh must not claim regional keys"
    createAgent(compute, REGION).agentType == "$ACCOUNT/$REGION/GoogleSslCertificateCachingAgent"
    createAgent(compute, "europe-west1").agentType == "$ACCOUNT/europe-west1/GoogleSslCertificateCachingAgent"
    createAgent(compute, null).agentType == "$ACCOUNT/GoogleSslCertificateCachingAgent"
  }

  void "a scope holding no certificates caches nothing"() {
    given:
    def compute = Mock(Compute)
    def regionSslCertificates = Mock(Compute.RegionSslCertificates)
    def regionSslCertificatesList = Mock(Compute.RegionSslCertificates.List)
    @Subject def agent = createAgent(compute, REGION)

    when:
    def cacheResult = agent.loadData(Mock(ProviderCache))

    then: "Compute omits items entirely rather than returning an empty list"
    1 * compute.regionSslCertificates() >> regionSslCertificates
    1 * regionSslCertificates.list(PROJECT, REGION) >> regionSslCertificatesList
    1 * regionSslCertificatesList.execute() >> new SslCertificateList()

    noExceptionThrown()
    !cacheResult.cacheResults[SSL_CERTIFICATES.ns]
  }

  private static GoogleSslCertificateCachingAgent createAgent(Compute compute, String region) {
    def credentials = new GoogleNamedAccountCredentials.Builder()
      .name(ACCOUNT)
      .project(PROJECT)
      .compute(compute)
      .build()

    new GoogleSslCertificateCachingAgent(
      "clouddriver",
      credentials,
      new ObjectMapper(),
      new DefaultRegistry(),
      region)
  }
}
