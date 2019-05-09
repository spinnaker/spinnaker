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
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

class AmazonSecurityGroupCachingAgentSpec extends Specification {

  static final String region = 'region'
  static final String account = 'account'

  AmazonEC2 ec2 = Mock(AmazonEC2)
  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) { getName() >> account }
  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2(_, _) >> ec2
    getLastModified() >> 12345L
  }
  ProviderCache providerCache = Mock(ProviderCache)
  ObjectMapper mapper = new AmazonObjectMapperConfigurer().createConfigured()
  EddaTimeoutConfig eddaTimeoutConfig = new EddaTimeoutConfig.Builder().build()

  @Subject AmazonSecurityGroupCachingAgent agent = new AmazonSecurityGroupCachingAgent(
    amazonClientProvider, creds, region, mapper, Spectator.registry(), eddaTimeoutConfig)

  SecurityGroup securityGroupA = new SecurityGroup(groupId: 'id-a', groupName: 'name-a', description: 'a')
  SecurityGroup securityGroupB = new SecurityGroup(groupId: 'id-b', groupName: 'name-b', description: 'b')
  String keyGroupA = Keys.getSecurityGroupKey(securityGroupA.groupName, securityGroupA.groupId, region, account, null)
  String keyGroupB = Keys.getSecurityGroupKey(securityGroupB.groupName, securityGroupB.groupId, region, account, null)

  void "should add security groups on initial run"() {
    given:
    DescribeSecurityGroupsResult result = new DescribeSecurityGroupsResult(
      securityGroups: [securityGroupA, securityGroupB])

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

  void "should prefer security groups from cache when on_demand record present"() {
    given:
    DescribeSecurityGroupsResult result = new DescribeSecurityGroupsResult(
      securityGroups: [securityGroupA, securityGroupB])
    def cred = TestCredential.named("test", [edda: "http://foo", eddaEnabled: true])
    def agent = new AmazonSecurityGroupCachingAgent(
      amazonClientProvider, cred, region, mapper, Spectator.registry(), eddaTimeoutConfig)
    CacheData onDemandResult = new DefaultCacheData(agent.lastModifiedKey, [lastModified: '12346'], [:])
    def existingIds = ['sg1', 'sg2']
    List<CacheData> existingCacheData = []

    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * ec2.describeSecurityGroups() >> result
    1 * providerCache.get(ON_DEMAND.ns, agent.lastModifiedKey) >> onDemandResult
    1 * providerCache.filterIdentifiers(SECURITY_GROUPS.ns, Keys.getSecurityGroupKey('*', '*', region, 'test', '*')) >> existingIds
    1 * providerCache.getAll(SECURITY_GROUPS.ns, existingIds) >> existingCacheData
    cache.cacheResults[SECURITY_GROUPS.ns] == existingCacheData

  }
}
