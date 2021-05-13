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
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import java.time.Instant

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
    onDemandType                | provider               || expected
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

  void "should evict processed onDemand entries"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def providerCache = Mock(ProviderCache)
    def id = "aws:stacks:account:region:arn:aws:cloudformation:region:accountid:stackname"
    def cacheData = new DefaultCacheData(id, (int) 20,
      ImmutableMap.of("cacheTime", (long) 10 , "processedCount", 1), ImmutableMap.of())


    when:
    agent.loadData(providerCache)

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ ]
    3 * providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,_) >> [ cacheData ]
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [ id ])
  }

  void "should insert onDemand requests into onDemand NS"() {
    given:
    def postData = [ credentials: "accountName", stackName: "stackName", region: ["region"]]
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def providerCache = Mock(ProviderCache)
    def stackChangeSetsResults = Mock(ListChangeSetsResult)

    when:
    agent.handle(providerCache, postData)

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]
    2 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResults
    2 * stackChangeSetsResults.getSummaries() >> new ArrayList()
    2 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, _)
    }


  void "should keep unprocessed onDemand entries"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def providerCache = Mock(ProviderCache)
    def id = "aws:stacks:account:region:arn:aws:cloudformation:region:accountid:stackname"
    def cacheData =  new DefaultCacheData(id, (int) 20,
      ImmutableMap.of("cacheTime", (long) 1, "processedCount", 0), ImmutableMap.of())

    when:
    agent.loadData(providerCache)

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ ]
    3 * providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,_) >> [ cacheData ]
    1 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData )
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [])
  }

  void "should keep newer onDemand entries"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def providerCache = Mock(ProviderCache)
    def now = Instant.now()
    def id = "aws:stacks:account:region:arn:aws:cloudformation:region:accountid:stackname"
    def cacheData = new DefaultCacheData(id, (int) 20,
      ImmutableMap.of("cacheTime", (long) now.plusMillis(100).toEpochMilli(),
      "processedCount", 1), ImmutableMap.of())

    when:
    agent.loadData(providerCache)

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResults
    1 * stackResults.stacks >> [ ]
    3 * providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,_) >> [ cacheData ]
    1 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [])
  }

  void "should paginate through all stacks"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResultFirstPage = Mock(DescribeStacksResult)
    def stackResultSecondPage = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")
    def stackChangeSetsResult = Mock(ListChangeSetsResult)
    def nextPageToken = "test pagination token"

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation

    // first page returns stack1
    1 * amazonCloudFormation.describeStacks({ it.getNextToken() == null }) >> stackResultFirstPage
    1 * stackResultFirstPage.stacks >> [stack1]
    2 * stackResultFirstPage.getNextToken() >> nextPageToken

    // second page returns stack2 and is the last one
    1 * amazonCloudFormation.describeStacks({ it.getNextToken() == nextPageToken }) >> stackResultSecondPage
    1 * stackResultSecondPage.stacks >> [stack2]
    1 * stackResultSecondPage.getNextToken() >> null

    // there are no ChangeSets
    2 * amazonCloudFormation.listChangeSets(_) >> stackChangeSetsResult
    2 * stackChangeSetsResult.getSummaries() >> new ArrayList()

    results.size() == 2
    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId
  }

  void "should paginate through all changesets"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResult = Mock(DescribeStacksResult)
    def stack = new Stack().withStackId("stack").withStackStatus("CREATE_SUCCESS")
    def stackChangeSetsResultFirstPage = Mock(ListChangeSetsResult)
    def stackChangeSetsResultSecondPage = Mock(ListChangeSetsResult)
    def changeSet1 = new ChangeSetSummary().withChangeSetName("changeSet1")
    def changeSet2 = new ChangeSetSummary().withChangeSetName("changeSet2")
    def describeChangeSetResult = Mock(DescribeChangeSetResult)
    def change = new Change().withType("type")
    def nextPageToken = "test pagination token"

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]
    def cachedStack = results.find {
      it.id == Keys.getCloudFormationKey("stack", "region", "accountName")
    }
    def cachedChangeSets = cachedStack.attributes.'changeSets'

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> stackResult
    1 * stackResult.stacks >> [stack]

    // first page returns changeSet1
    1 * amazonCloudFormation.listChangeSets({ it.getNextToken() == null }) >> stackChangeSetsResultFirstPage
    1 * stackChangeSetsResultFirstPage.getSummaries() >> [changeSet1]
    2 * stackChangeSetsResultFirstPage.getNextToken() >> nextPageToken

    // second page returns changeSet2 and is the last one
    1 * amazonCloudFormation.listChangeSets({ it.getNextToken() == nextPageToken }) >> stackChangeSetsResultSecondPage
    1 * stackChangeSetsResultSecondPage.getSummaries() >> [changeSet2]
    1 * stackChangeSetsResultSecondPage.getNextToken() >> null

    // return a Change for each ChangeSet
    2 * amazonCloudFormation.describeChangeSet(_) >> describeChangeSetResult
    2 * describeChangeSetResult.getChanges() >> [change]

    cachedChangeSets.size() == 2
    cachedChangeSets.any { it.name == changeSet1.getChangeSetName() }
    cachedChangeSets.any { it.name == changeSet2.getChangeSetName() }
  }
}
