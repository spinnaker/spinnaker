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
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

class AmazonSecurityGroupCachingAgentSpec extends Specification {

  static final String region = 'region'
  static final String account = 'account'

  AmazonEC2 ec2 = Mock(AmazonEC2)
  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) { getName() >> account }
  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) { getAmazonEC2(creds, region) >> ec2 }
  ProviderCache providerCache = Mock(ProviderCache)
  AmazonObjectMapper mapper = new AmazonObjectMapper()

  @Subject AmazonSecurityGroupCachingAgent agent = new AmazonSecurityGroupCachingAgent(
    amazonClientProvider, creds, region, mapper, Spectator.registry())

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
    with (cache.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns)) { List<CacheData> cd ->
      cd.size() == 2
      cd.id.containsAll([keyGroupA, keyGroupB])
    }
    0 * _
  }
}
