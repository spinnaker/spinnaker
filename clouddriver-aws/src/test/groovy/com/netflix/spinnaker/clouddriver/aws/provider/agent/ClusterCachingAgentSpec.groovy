/*
 * Copyright 2016 Netflix, Inc.
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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.SuspendedProcess
import com.amazonaws.services.autoscaling.model.TagDescription
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ClusterCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'
  static String accountId = 'accountId'

  static int defaultMin = 1
  static int defaultMax = 1
  static int defaultDesired = 1
  static Collection<String> defaultSuspendedProcesses = ["Launch"]
  static String vpc = "vpc-1"

  AutoScalingGroup defaultAsg = new AutoScalingGroup()
    .withAutoScalingGroupName("test-v001")
    .withDesiredCapacity(defaultDesired)
    .withMinSize(defaultMin)
    .withMaxSize(defaultMax)
    .withVPCZoneIdentifier("subnetId1,subnetId2")
    .withSuspendedProcesses(defaultSuspendedProcesses.collect { new SuspendedProcess().withProcessName(it) }
  )

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  AmazonEC2 ec2 = Mock(AmazonEC2)

  @Shared
  EddaTimeoutConfig edda = Mock(EddaTimeoutConfig)

  @Shared
  AmazonCachingAgentFilter filter = new AmazonCachingAgentFilter()

  def getAgent() {
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
      it.getAccountId() >> accountId
    }
    def cloud = Stub(AmazonCloudProvider)
    def client = Stub(AmazonClientProvider) {
      getAmazonEC2(creds, region, _) >> ec2
    }
    new ClusterCachingAgent(cloud, client, creds, region, AmazonObjectMapperConfigurer.createConfigured(), Spectator.globalRegistry(), edda, filter)
  }

  @Unroll
  def "should compare capacity and suspended processes when determining if ASGs are similar"() {
    given:
    def asg = new AutoScalingGroup().withDesiredCapacity(desired).withMinSize(min).withMaxSize(max).withSuspendedProcesses(
      suspendedProcesses.collect { new SuspendedProcess().withProcessName(it) }
    )

    when:
    ClusterCachingAgent.areSimilarAutoScalingGroups(defaultAsg, asg) == areSimilar

    then:
    true

    where:
    min        | max        | desired        | suspendedProcesses        || areSimilar
    defaultMin | defaultMax | defaultDesired | defaultSuspendedProcesses || true
    0          | defaultMax | defaultDesired | defaultSuspendedProcesses || false
    defaultMin | 0          | defaultDesired | defaultSuspendedProcesses || false
    defaultMin | defaultMax | 0              | defaultSuspendedProcesses || false
    defaultMin | defaultMax | defaultDesired | []                        || false
  }

  @Unroll
  def "should still index asg if VPCZoneIdentifier contains a deleted subnet"() {
    when:
    def asgData = new ClusterCachingAgent.AsgData(defaultAsg, null, null, "test", "us-west-1", subnetMap)

    then:
    asgData.vpcId == vpc

    where:
    subnetMap | _
    [subnetId1: (vpc), subnetId2: (vpc)] | _
    [subnetId2: (vpc)] | _
  }

  def "should throw exception if VPCZoneIdentifier contains subnets from multiple vpcs"() {
    given:
    def subnetMap = [subnetId1: (vpc), subnetId2: "otherVPC"]

    when:
    new ClusterCachingAgent.AsgData(defaultAsg, null, null, "test", "us-west-1", subnetMap)

    then:
    def e = thrown(RuntimeException)
    e.message.startsWith("failed to resolve only one vpc")
  }

  def "on demand update result should have authoritative types correctly set"() {
    given:
    def agent = getAgent()
    def data = [
      asgName: "asgName",
      serverGroupName: "serverGroupName",
      region: region,
      account: accountName
    ]

    when:
    def result = agent.handle(providerCache, data)

    then:
    result.authoritativeTypes as Set == ["serverGroups"] as Set
  }

  void "asg should filter excluded tags"() {
    given:
    def agent = getAgent()
    def client = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, _) >> Stub(AmazonAutoScaling) {
        describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult() {
          List<AutoScalingGroup> getAutoScalingGroups() {
            return filterableASGs
          }
        }
      }
    }

    def clients = new ClusterCachingAgent.AmazonClients(client, agent.account, agent.region, false)
    filter.includeTags = includeTags
    filter.excludeTags = excludeTags

    when:
    def result = agent.loadAutoScalingGroups(clients)

    then:
    result.asgs*.autoScalingGroupName == expected

    where:
    includeTags                   | excludeTags                   | expected
    null                          | null                          | filterableASGs*.autoScalingGroupName
    [taggify("hello")]            | null                          | ["test-hello-tag-value", "test-hello-tag-value-different", "test-hello-tag-no-value"]
    [taggify("hello", "goodbye")] | null                          | ["test-hello-tag-value"]
    [taggify("hello", "goo")]     | null                          | []
    [taggify("hello", ".*bye")]   | null                          | ["test-hello-tag-value"]
    [taggify(".*a.*")]            | null                          | ["test-no-hello-tag"]
    null                          | [taggify("hello")]            | ["test-no-hello-tag", "test-no-tags"]
    null                          | [taggify("hello", "goodbye")] | ["test-hello-tag-value-different", "test-hello-tag-no-value", "test-no-hello-tag", "test-no-tags"]
    [taggify("hello", "goodbye")] | [taggify("hello")]            | []
    [taggify(".*", "ciao")]       | [taggify("hello", ".*")]      | []
  }

  private static final List<AutoScalingGroup> filterableASGs = [
    new AutoScalingGroup()
      .withAutoScalingGroupName("test-hello-tag-value")
      .withTags(new TagDescription().withKey("hello").withValue("goodbye")),
    new AutoScalingGroup()
      .withAutoScalingGroupName("test-hello-tag-value-different")
      .withTags(new TagDescription().withKey("hello").withValue("ciao")),
    new AutoScalingGroup()
      .withAutoScalingGroupName("test-hello-tag-no-value")
      .withTags(new TagDescription().withKey("hello")),
    new AutoScalingGroup()
      .withAutoScalingGroupName("test-no-hello-tag")
      .withTags(new TagDescription().withKey("Name")),
    new AutoScalingGroup()
      .withAutoScalingGroupName("test-no-tags"),
  ]

  private static def taggify(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.TagFilterOption(name, value)
  }

  private SuspendedProcess sP(String processName) {
    return new SuspendedProcess().withProcessName(processName)
  }
}
