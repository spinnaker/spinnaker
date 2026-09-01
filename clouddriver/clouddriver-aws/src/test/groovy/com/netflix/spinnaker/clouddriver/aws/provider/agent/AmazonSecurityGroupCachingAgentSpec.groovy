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
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse
import software.amazon.awssdk.services.ec2.model.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

class AmazonSecurityGroupCachingAgentSpec extends Specification {

  static final String region = 'region'
  static final String account = 'account'

  Ec2Client ec2 = Mock(Ec2Client)
  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) { getName() >> account }
  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2V2(_, _) >> ec2
  }
  ProviderCache providerCache = Mock(ProviderCache)
  ObjectMapper mapper = new AmazonObjectMapperConfigurer().createConfigured().registerModule(new AwsSdkV2Module())

  @Subject AmazonSecurityGroupCachingAgent agent = new AmazonSecurityGroupCachingAgent(
    amazonClientProvider, creds, region, mapper, Spectator.registry())

  SecurityGroup securityGroupA = SecurityGroup.builder().groupId('id-a').groupName('name-a').description('a').build()
  SecurityGroup securityGroupB = SecurityGroup.builder().groupId('id-b').groupName('name-b').description('b').build()
  String keyGroupA = Keys.getSecurityGroupKey(securityGroupA.groupName(), securityGroupA.groupId(), region, account, null)
  String keyGroupB = Keys.getSecurityGroupKey(securityGroupB.groupName(), securityGroupB.groupId(), region, account, null)

  void "should add security groups on initial run"() {
    given:
    DescribeSecurityGroupsResponse result = DescribeSecurityGroupsResponse.builder()
      .securityGroups(securityGroupA, securityGroupB).build()

    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * ec2.describeSecurityGroups() >> result
    with (cache.cacheResults.get(SECURITY_GROUPS.ns)) { List<CacheData> cd ->
      cd.size() == 2
      cd.id.containsAll([keyGroupA, keyGroupB])
    }
    0 * _
  }

  void "should get correct cache key pattern"() {
    given:
    def agent = getAgent()

    when:
    def cacheKeyPatterns = agent.getCacheKeyPatterns()

    then:
    cacheKeyPatterns.isPresent()
    cacheKeyPatterns.get() == [
      (SECURITY_GROUPS.ns): "aws:securityGroups:*:*:region:account:*"
    ]
  }
}
