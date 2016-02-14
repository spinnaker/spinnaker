/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.ec2.model.DescribeAddressesResult
import com.amazonaws.services.ec2.model.DomainType
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonElasticIpCachingAgentSpec extends Specification {
  static String region = 'region'
  static String account = 'account'

  @Subject
  AmazonElasticIpCachingAgent agent

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  AmazonCloudProvider amazonCloudProvider

  @Shared
  AmazonEC2 ec2

  @Shared
  Address eipA = new Address().withPublicIp("10.0.0.1").withDomain(DomainType.Standard).withInstanceId("i-123456")

  @Shared
  String eipAKey = Keys.getElasticIpKey(new AmazonCloudProvider(), eipA.publicIp, region, account)

  @Shared
  Address eipB = new Address().withPublicIp("10.0.0.2").withDomain(DomainType.Vpc)

  @Shared
  String eipBKey = Keys.getElasticIpKey(new AmazonCloudProvider(), eipB.publicIp, region, account)

  def setup() {
    amazonCloudProvider = new AmazonCloudProvider()
    ec2 = Mock(AmazonEC2)
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> account
    }
    def acp = Stub(AmazonClientProvider) {
      getAmazonEC2(creds, region) >> ec2
    }
    agent = new AmazonElasticIpCachingAgent(amazonCloudProvider, acp, creds, region)
  }

  void "should add elastic ips on initial run"() {
    given:
    def addr = new DescribeAddressesResult().withAddresses([eipA, eipB])

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeAddresses() >> addr
    0 * _

    result.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    result.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey }
  }

  void "should evict elastic ips when not found on subsequent runs"() {
    given:
    def result = Mock(DescribeAddressesResult)

    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * result.getAddresses() >> [eipA, eipB]
    1 * ec2.describeAddresses() >> result
    0 * _

    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey }

    when:
    cache = agent.loadData(providerCache)

    then:
    1 * result.getAddresses() >> [eipA]
    1 * ec2.describeAddresses() >> result
    0 * _

    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey } == null
  }
}
