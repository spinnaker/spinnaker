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

import com.netflix.spectator.api.Registry
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.Change
import software.amazon.awssdk.services.cloudformation.model.ChangeSetSummary
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsResponse
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse
import software.amazon.awssdk.services.cloudformation.model.ListChangeSetsRequest
import software.amazon.awssdk.services.cloudformation.model.ListChangeSetsResponse
import software.amazon.awssdk.services.cloudformation.model.Stack
import software.amazon.awssdk.services.cloudformation.model.StackEvent
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
  AmazonClientProvider acp

  @Shared
  Registry registry

  def setup() {
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
    }
    acp = Mock(AmazonClientProvider)
    registry = Mock(Registry)
    agent = new AmazonCloudFormationCachingAgent(acp, creds, region, registry)
  }

  void "should add cloud formations on initial run"() {
    given:
    def cloudFormationClient = Mock(CloudFormationClient)
    def stack1 = Stack.builder().stackId("stack1").stackStatus("CREATE_SUCCESS").build()
    def stack2 = Stack.builder().stackId("stack2").stackStatus("CREATE_SUCCESS").build()

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([stack1, stack2]).build()
    }
    2 * cloudFormationClient.listChangeSets(_ as ListChangeSetsRequest) >> {
      ListChangeSetsResponse.builder().summaries([]).build()
    }

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId()
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId()
  }

  @Unroll
  void "should include stack status reason when state is ROLLBACK_COMPLETE (failed)"() {
    given:
    def cloudFormationClient = Mock(CloudFormationClient)
    def stack = Stack.builder().stackId("stack1").stackStatus(stackStatus).build()
    def stackEvent = StackEvent.builder().resourceStatus(resourceStatus).resourceStatusReason(expectedReason).build()

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([stack]).build()
    }
    1 * cloudFormationClient.listChangeSets(_ as ListChangeSetsRequest) >> {
      ListChangeSetsResponse.builder().summaries([]).build()
    }
    1 * cloudFormationClient.describeStackEvents(_ as DescribeStackEventsRequest) >> {
      DescribeStackEventsResponse.builder().stackEvents([stackEvent]).build()
    }

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
    def cloudFormationClient = Mock(CloudFormationClient)
    def stack = Stack.builder().stackId("stack1").stackStatus("CREATE_COMPLETE").build()
    def changeSet = ChangeSetSummary.builder()
      .changeSetName("name")
      .status("status")
      .statusReason("statusReason")
      .build()
    def change = Change.builder().type("Resource").build()

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]
    def cachedStack = results.find {
      it.id == Keys.getCloudFormationKey("stack1", "region", "accountName")
    }
    def cachedChangeSets = cachedStack.attributes.'changeSets'

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([stack]).build()
    }
    1 * cloudFormationClient.listChangeSets(_ as ListChangeSetsRequest) >> {
      ListChangeSetsResponse.builder().summaries([changeSet]).build()
    }
    1 * cloudFormationClient.describeChangeSet(_ as DescribeChangeSetRequest) >> {
      DescribeChangeSetResponse.builder().changes([change]).build()
    }

    cachedChangeSets.size() == 1
    with (cachedChangeSets.get(0)) {
      name == "name"
      status == "status"
      statusReason == "statusReason"
      changes.size() == 1
      changes.get(0).type == "Resource"
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
    [:]                                           | true // backwards compatibility
    [credentials: accountName, region: [region]]  | true
    [credentials: null, region: null]             | false
    [credentials: accountName, region: null]      | false
    [credentials: null, region: [region]]         | false
    [credentials: "other", region: [region]]      | false
    [credentials: accountName, region: ["other"]] | false
    [credentials: "other", region: ["other"]]     | false
  }

  void "should paginate through all stacks"() {
    given:
    def cloudFormationClient = Mock(CloudFormationClient)
    def stack1 = Stack.builder().stackId("stack1").stackStatus("CREATE_SUCCESS").build()
    def stack2 = Stack.builder().stackId("stack2").stackStatus("CREATE_SUCCESS").build()
    def nextPageToken = "test pagination token"

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.STACKS.ns]

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient

    // first page returns stack1
    1 * cloudFormationClient.describeStacks({ it.nextToken() == null } as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([stack1]).nextToken(nextPageToken).build()
    }

    // second page returns stack2 and is the last one
    1 * cloudFormationClient.describeStacks({ it.nextToken() == nextPageToken } as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([stack2]).build()
    }

    // there are no ChangeSets
    2 * cloudFormationClient.listChangeSets(_ as ListChangeSetsRequest) >> {
      ListChangeSetsResponse.builder().summaries([]).build()
    }

    results.size() == 2
    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "accountName") }.attributes.'stackId' == stack1.stackId()
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "accountName") }.attributes.'stackId' == stack2.stackId()
  }

  void "should evict processed onDemand entries"() {
    given:
    def cloudFormationClient = Mock(CloudFormationClient)
    def providerCache = Mock(ProviderCache)
    def id = "aws:stacks:account:region:arn:aws:cloudformation:region:accountid:stackname"
    def cacheData = new DefaultCacheData(id, (int) 20,
      ImmutableMap.of("cacheTime", (long) 10, "processedCount", 1), ImmutableMap.of())

    when:
    agent.loadData(providerCache)

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([]).build()
    }
    3 * providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, _) >> [cacheData]
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [id])
  }

  void "should keep unprocessed onDemand entries"() {
    given:
    def cloudFormationClient = Mock(CloudFormationClient)
    def providerCache = Mock(ProviderCache)
    def id = "aws:stacks:account:region:arn:aws:cloudformation:region:accountid:stackname"
    def cacheData = new DefaultCacheData(id, (int) 20,
      ImmutableMap.of("cacheTime", (long) 1, "processedCount", 0), ImmutableMap.of())

    when:
    agent.loadData(providerCache)

    then:
    1 * acp.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([]).build()
    }
    3 * providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, _) >> [cacheData]
    1 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [])
  }
}
