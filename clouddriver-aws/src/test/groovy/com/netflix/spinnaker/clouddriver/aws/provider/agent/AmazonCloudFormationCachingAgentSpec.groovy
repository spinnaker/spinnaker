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
import com.amazonaws.services.cloudformation.model.Change
import com.amazonaws.services.cloudformation.model.ChangeSetSummary
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult
import com.amazonaws.services.cloudformation.model.DescribeStacksResult
import com.amazonaws.services.cloudformation.model.ListChangeSetsResult
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
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
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
    def stackChangeSetsResults = Mock(ListChangeSetsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]
    2 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    2 * stackChangeSetsResults.getSummaries() >> new ArrayList()

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId
  }

  void "should evict cloudformations when not found on subsequent runs"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")
    def stackChangeSetsResults = Mock(ListChangeSetsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]
    2 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    2 * stackChangeSetsResults.getSummaries() >> new ArrayList()

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId

    when:
    cache = agent.loadData(providerCache)
    results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ stack1 ]
    1 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    1 * stackChangeSetsResults.getSummaries() >> new ArrayList()

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") } == null
  }

  @Unroll
  void "should include stack status reason when state is ROLLBACK_COMPLETE (failed)"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stack = new Stack().withStackId("stack1").withStackStatus(stackStatus)
    def stackResults = Mock(DescribeStacksResult)
    def stackEvent = new StackEvent().withResourceStatus(resourceStatus).withResourceStatusReason(expectedReason)
    def stackEventResults = Mock(DescribeStackEventsResult)
    def stackChangeSetsResults = Mock(ListChangeSetsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    1 * stackChangeSetsResults.getSummaries() >> new ArrayList()
    1 * stackResults.stacks >> [ stack ]
    1 * amazonCloudFormation.describeStackEvents(_) >> stackEventResults
    1 * stackEventResults.getStackEvents() >> [ stackEvent ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackStatusReason' == expectedReason

    where:
    resourceStatus  | stackStatus                || expectedReason
    'CREATE_FAILED' | 'ROLLBACK_COMPLETE'        || "create failed"
    'UPDATE_FAILED' | 'ROLLBACK_COMPLETE'        || "update failed"
    'CREATE_FAILED' | 'UPDATE_ROLLBACK_COMPLETE' || "create failed"
    'UPDATE_FAILED' | 'UPDATE_ROLLBACK_COMPLETE' || "update failed"
  }

  void "should include stack change sets if any available"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stack = new Stack().withStackId("stack1").withStackStatus("CREATE_COMPLETE")
    def stackResults = Mock(DescribeStacksResult)
    def listChangeSetsResult = Mock(ListChangeSetsResult)
    def changeSet = new ChangeSetSummary()
      .withChangeSetName("name")
      .withStatus("status")
      .withStatusReason("statusReason")
    def describeChangeSetResult = Mock(DescribeChangeSetResult)
    def change = new Change().withType("type")

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]
    def cachedStack = results.find {
      it.id == Keys.getCloudFormationKey("stack1", "region", "accountName")
    }
    def cachedCangeSets = cachedStack.attributes.'changeSets'

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * amazonCloudFormation.listChangeSets(_) >> listChangeSetsResult
    1 * listChangeSetsResult.getSummaries() >> Collections.singletonList(changeSet)
    1 * amazonCloudFormation.describeChangeSet(_) >> describeChangeSetResult
    1 * describeChangeSetResult.getChanges() >> Collections.singletonList(change)
    1 * stackResults.stacks >> [ stack ]

    cachedCangeSets.size() == 1
    with (cachedCangeSets.get(0)) {
      name == "name"
      status == "status"
      statusReason == "statusReason"
      changes.size() == 1
      with(changes.get(0)) {
        type == "type"
      }
    }

  }

  @Unroll
  void "OnDemand request should be handled for type '#onDemandType' and provider '#provider': '#expected'"() {
    when:
    def result = agent.handles(onDemandType, provider)

    then:
    result == expected

    where:
    onDemandType                              | provider               || expected
    OnDemandType.CloudFormation | AmazonCloudProvider.ID || true
    OnDemandType.CloudFormation | "other"                || false
    OnDemandType.Job            | AmazonCloudProvider.ID || false
  }

  @Unroll
  void "OnDemand request should be handled for the specific account and region"() {
    when:
    def result = agent.shouldHandle(data)

    then:
    result == expected

    where:
    data                                          | expected
    [:]                                           | true // backwards compatiblity
    [credentials: accountName, region: [region]]  | true
    [credentials: null, region: null]             | false
    [credentials: accountName, region: null]      | false
    [credentials: null, region: [region]]         | false
    [credentials: "other", region: [region]]      | false
    [credentials: accountName, region: ["other"]] | false
    [credentials: "other", region: ["other"]]     | false
  }

  void "OnDemand handle method should get the same cache data as when reloading the cache"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")
    def stackChangeSetsResults = Mock(ListChangeSetsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = agent.handle(providerCache, Collections.emptyMap())

    then:
    2 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    2 * amazonCloudFormation.describeStacks(_) >> stackResults
    2 * stackResults.stacks >> [ stack1, stack2 ]
    4 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    4 * stackChangeSetsResults.getSummaries() >> new ArrayList()

    def expected = cache.cacheResults.get(Keys.Namespace.STACKS.ns).collect { it.attributes } as Set
    def onDemand = results.cacheResult.cacheResults.get(Keys.Namespace.STACKS.ns).collect { it.attributes } as Set
    expected == onDemand
  }
}
