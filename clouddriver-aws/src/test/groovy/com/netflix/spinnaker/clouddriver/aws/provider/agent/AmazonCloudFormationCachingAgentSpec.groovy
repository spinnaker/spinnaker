/*
 * Copyright (c) 2019 Schibsted Media Group.
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

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult
import com.amazonaws.services.cloudformation.model.DescribeStacksResult
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.cloudformation.model.StackEvent
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AmazonCloudFormationCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'

  @Subject
  AmazonCloudFormationCachingAgent agent

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  AmazonEC2 ec2

  @Shared
  AmazonClientProvider acp

  @Shared
  Registry registry

  def setup() {
    ec2 = Mock(AmazonEC2)
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
    }
    acp = Mock(AmazonClientProvider)
    registry = Mock(Registry)
    agent = new AmazonCloudFormationCachingAgent(acp, creds, region, registry)
  }

  void "should add cloud formations on initial run"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId
  }

  void "should evict cloudformations when not found on subsequent runs"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId

    when:
    cache = agent.loadData(providerCache)
    results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") } == null
  }

  void "should include stack status reason when state is ROLLBACK_COMPLETE (failed)"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stack = new Stack().withStackId("stack1").withStackStatus("ROLLBACK_COMPLETE")
    def stackResults = Mock(DescribeStacksResult)
    def stackEvent = new StackEvent().withResourceStatus("CREATE_FAILED").withResourceStatusReason("who knows")
    def stackEventResults = Mock(DescribeStackEventsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack ]
    1 * amazonCloudFormation.describeStackEvents(_) >> stackEventResults
    1 * stackEventResults.getStackEvents() >> [ stackEvent ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackStatusReason' == 'who knows'
  }

  @Unroll
  void "OnDemand request should be handled for type '#onDemandType' and provider '#provider': '#expected'"() {
    when:
    def result = agent.handles(onDemandType, provider)

    then:
    result == expected

    where:
    onDemandType                              | provider               || expected
    OnDemandAgent.OnDemandType.CloudFormation | AmazonCloudProvider.ID || true
    OnDemandAgent.OnDemandType.CloudFormation | "other"                || false
    OnDemandAgent.OnDemandType.Job            | AmazonCloudProvider.ID || false
  }

  void "OnDemand handle method should get the same cache data as when reloading the cache"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")

    when:
    def cache = agent.loadData(providerCache)
    def results = agent.handle(providerCache, Collections.emptyMap())

    then:
    2 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    2 * amazonCloudFormation.describeStacks() >> stackResults
    2 * stackResults.stacks >> [ stack1, stack2 ]

    def expected = cache.cacheResults.get(Keys.Namespace.STACKS.ns).collect { it.attributes } as Set
    def onDemand = results.cacheResult.cacheResults.get(Keys.Namespace.STACKS.ns).collect { it.attributes } as Set
    expected == onDemand
  }
}
