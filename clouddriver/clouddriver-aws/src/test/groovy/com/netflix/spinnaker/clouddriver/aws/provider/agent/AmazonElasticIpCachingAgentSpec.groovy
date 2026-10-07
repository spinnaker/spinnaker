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

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Address
import software.amazon.awssdk.services.ec2.model.DescribeAddressesResponse
import software.amazon.awssdk.services.ec2.model.DomainType
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
  Ec2Client ec2

  @Shared
  Address eipA = Address.builder().publicIp("10.0.0.1").domain(DomainType.STANDARD).instanceId("i-123456").build()

  @Shared
  String eipAKey = Keys.getElasticIpKey(eipA.publicIp(), region, account)

  @Shared
  Address eipB = Address.builder().publicIp("10.0.0.2").domain(DomainType.VPC).build()

  @Shared
  String eipBKey = Keys.getElasticIpKey(eipB.publicIp(), region, account)

  def setup() {
    ec2 = Mock(Ec2Client)
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> account
    }
    def acp = Stub(AmazonClientProvider) {
      getAmazonEC2V2(creds, region) >> ec2
    }
    agent = new AmazonElasticIpCachingAgent(acp, creds, region)
  }

  void "should add elastic ips on initial run"() {
    given:
    def addr = DescribeAddressesResponse.builder().addresses([eipA, eipB]).build()

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeAddresses() >> addr
    0 * _

    result.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    result.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey }
  }

  void "should evict elastic ips when not found on subsequent runs"() {
    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * ec2.describeAddresses() >> DescribeAddressesResponse.builder().addresses([eipA, eipB]).build()
    0 * _

    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey }

    when:
    cache = agent.loadData(providerCache)

    then:
    1 * ec2.describeAddresses() >> DescribeAddressesResponse.builder().addresses([eipA]).build()
    0 * _

    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipAKey }
    cache.cacheResults[Keys.Namespace.ELASTIC_IPS.ns].find { it.id == eipBKey } == null
  }
}
