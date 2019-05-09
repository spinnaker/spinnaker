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
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

class AmazonSubnetCachingAgentSpec extends Specification {
  static final String account = 'test'
  static final String region = 'us-east-1'


  AmazonEC2 ec2 = Mock(AmazonEC2)

  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) {
    getName() >> account
  }

  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2(creds, region) >> ec2
  }

  ProviderCache providerCache = Mock(ProviderCache)

  ObjectMapper amazonObjectMapper = new AmazonObjectMapperConfigurer().createConfigured()

  @Subject
  AmazonSubnetCachingAgent agent = new AmazonSubnetCachingAgent(
      amazonClientProvider, creds, region, amazonObjectMapper)

  void "should add on initial run"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeSubnets() >> new DescribeSubnetsResult(subnets: [
      new Subnet(subnetId: 'subnetId1'),
      new Subnet(subnetId: 'subnetId2')
    ])
    0 * _

    with (result.cacheResults.get(Keys.Namespace.SUBNETS.ns)) { List<CacheData> cd ->
      cd.size() == 2
      cd.find { it.id == Keys.getSubnetKey('subnetId1', region, account)}
      cd.find { it.id == Keys.getSubnetKey('subnetId2', region, account)}
    }
  }
}
