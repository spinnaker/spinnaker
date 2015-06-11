/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.mort.aws.cache

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.ec2.model.DescribeAddressesResult
import com.amazonaws.services.ec2.model.DomainType
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.InMemoryCacheService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonElasticIpCachingAgentSpec extends Specification {
  static String region = 'region'
  static String account = 'account'

  @Subject
  AmazonElasticIpCachingAgent agent

  @Shared
  AmazonEC2 ec2

  @Shared
  Address eipA = new Address().withPublicIp("10.0.0.1").withDomain(DomainType.Standard).withInstanceId("i-123456")

  @Shared
  String eipAKey = Keys.getElasticIpKey(eipA.publicIp, region, account)

  @Shared
  Address eipB = new Address().withPublicIp("10.0.0.2").withDomain(DomainType.Vpc)

  @Shared
  String eipBKey = Keys.getElasticIpKey(eipB.publicIp, region, account)

  def setup() {
    ec2 = Mock(AmazonEC2)
    agent = new AmazonElasticIpCachingAgent(
        cacheService: new InMemoryCacheService(),
        ec2: ec2,
        region: region,
        account: account)
  }

  void "should add elastic ips on initial run"() {
    given:
    def result = Mock(DescribeAddressesResult)

    when:
    agent.call()

    then:
    1 * result.getAddresses() >> [eipA, eipB]
    1 * ec2.describeAddresses() >> result
    0 * _

    agent.cacheService.exists(eipAKey)
    agent.cacheService.exists(eipBKey)
  }

  void "should evict elastic ips when not found on subsequent runs"() {
    given:
    def result = Mock(DescribeAddressesResult)

    when:
    agent.call()

    then:
    1 * result.getAddresses() >> [eipA, eipB]
    1 * ec2.describeAddresses() >> result
    0 * _

    agent.cacheService.exists(eipAKey)
    agent.cacheService.exists(eipBKey)

    when:
    agent.call()

    then:
    1 * result.getAddresses() >> [eipA]
    1 * ec2.describeAddresses() >> result
    0 * _

    agent.cacheService.exists(eipAKey)
    !agent.cacheService.exists(eipBKey)
  }
}
