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
import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsRequest
import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsResult
import com.amazonaws.services.ec2.model.ReservedInstancesOffering
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeCachingAgentSpec extends Specification {

  static final String account = 'test'
  static final String region = 'us-east-1'

  AmazonEC2 ec2 = Mock(AmazonEC2)
  AmazonClientProvider provider = Stub(AmazonClientProvider) {
    getAmazonEC2(_, _) >> ec2
  }

  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) {
    getName() >> account
  }

  ProviderCache providerCache = Mock(ProviderCache)

  @Subject
  AmazonInstanceTypeCachingAgent agent = new AmazonInstanceTypeCachingAgent(provider, creds, region)

  void "should add to cache"() {
    when:
    def result = agent.loadData(providerCache)
    def expected = Keys.getInstanceTypeKey('m1', region, account)

    then:
    1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()) >> new DescribeReservedInstancesOfferingsResult(
      reservedInstancesOfferings: [
        new ReservedInstancesOffering(reservedInstancesOfferingId: '1', instanceType: 'm1')
      ]
    )
    with (result.cacheResults.get(Keys.Namespace.INSTANCE_TYPES.ns)) { List<CacheData> cd ->
      cd.size() == 1
      cd.find { it.id == expected }
    }
    0 * _
  }

  void "should dedupe instance types"() {
    when:
    def result = agent.loadData(providerCache)
    def expected = Keys.getInstanceTypeKey('m1', region, account)

    then:
    1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()) >> new DescribeReservedInstancesOfferingsResult(
      reservedInstancesOfferings: [
        new ReservedInstancesOffering(reservedInstancesOfferingId: '1', instanceType: 'm1'),
        new ReservedInstancesOffering(reservedInstancesOfferingId: '2', instanceType: 'm1'),
      ]
    )
    with (result.cacheResults.get(Keys.Namespace.INSTANCE_TYPES.ns)) { List<CacheData> cd ->
      cd.size() == 1
      cd.find { it.id == expected }
    }
    0 * _
  }

  void "should add all from paged results"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()) >> new DescribeReservedInstancesOfferingsResult(
      reservedInstancesOfferings: [
        new ReservedInstancesOffering(reservedInstancesOfferingId: '1', instanceType: 'm1'),
        new ReservedInstancesOffering(reservedInstancesOfferingId: '2', instanceType: 'm2')
      ],
      nextToken: 'moar'
    )
    1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest(nextToken: 'moar')) >> new DescribeReservedInstancesOfferingsResult(
      reservedInstancesOfferings: [
        new ReservedInstancesOffering(reservedInstancesOfferingId: '3', instanceType: 'm3')
      ]
    )

    with (result.cacheResults.get(Keys.Namespace.INSTANCE_TYPES.ns)) { List<CacheData> cd ->
      cd.size() == 3
      cd.find { it.id == Keys.getInstanceTypeKey('m1', region, account) }
      cd.find { it.id == Keys.getInstanceTypeKey('m2', region, account) }
      cd.find { it.id == Keys.getInstanceTypeKey('m3', region, account) }
    }
    0 * _
  }

}
