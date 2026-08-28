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

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplate
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.autoscaling.model.SuspendedProcess
import software.amazon.awssdk.services.autoscaling.model.TagDescription
import software.amazon.awssdk.services.ec2.Ec2Client
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

class ClusterCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'
  static String accountId = 'accountId'

  static int defaultMin = 1
  static int defaultMax = 1
  static int defaultDesired = 1
  static Collection<String> defaultSuspendedProcesses = ["Launch"]
  static String vpc = "vpc-1"

  AutoScalingGroup defaultAsg = AutoScalingGroup.builder()
    .autoScalingGroupName("test-v001")
    .desiredCapacity(defaultDesired)
    .minSize(defaultMin)
    .maxSize(defaultMax)
    .vpcZoneIdentifier("subnetId1,subnetId2")
    .suspendedProcesses(defaultSuspendedProcesses.collect { SuspendedProcess.builder().processName(it).build() })
    .build()

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  Ec2Client ec2 = Mock(Ec2Client)

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
      getAmazonEC2V2(creds, region) >> ec2
    }
    new ClusterCachingAgent(cloud, client, creds, region, AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module()), Spectator.globalRegistry(), edda, filter)
  }

  @Unroll
  def "should compare capacity and suspended processes when determining if ASGs are similar"() {
    given:
    def asg = AutoScalingGroup.builder().desiredCapacity(desired).minSize(min).maxSize(max).suspendedProcesses(
      suspendedProcesses.collect { SuspendedProcess.builder().processName(it).build() }
    ).build()

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

  @Unroll
  def "should create launchTemplate/Config key correctly for all types of asg"() {
    given:
    AutoScalingGroup asg = AutoScalingGroup.builder()
      .autoScalingGroupName("app-stack-v000")
      .desiredCapacity(defaultDesired)
      .minSize(defaultMin)
      .maxSize(defaultMax)
      ."$asgPropKey"(asgPropValue)
      .build()

    when:
    def asgData = new ClusterCachingAgent.AsgData(asg, null, null, "acc", "us-west-1", null)

    then:
    asgData.launchConfig == launchConfigKey
    asgData.launchTemplate == launchTemplateKey

    where:
    asgPropKey                | asgPropValue                                                                   || launchTemplateKey                        || launchConfigKey
    "launchConfigurationName" | "launchConfig-1"                                                                || null                                      || "aws:launchConfigs:acc:us-west-1:launchConfig-1"
    "launchTemplate"          | LaunchTemplateSpecification.builder()
                                   .launchTemplateName("lt-1")
                                   .version("2")
                                   .build()                                                                     || "aws:launchTemplates:acc:us-west-1:lt-1" || null
    "mixedInstancesPolicy"    | MixedInstancesPolicy.builder()
                                   .launchTemplate(LaunchTemplate.builder()
                                     .launchTemplateSpecification(
                                       LaunchTemplateSpecification.builder()
                                         .launchTemplateName("lt-1")
                                         .version("\$Latest")
                                         .build()
                                     )
                                     .build())
                                   .build()                                                                     || "aws:launchTemplates:acc:us-west-1:lt-1" || null
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
      getAutoScalingV2(_, _) >> Stub(AutoScalingClient) {
        describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(filterableASGs).build()
      }
    }

    def clients = new ClusterCachingAgent.AmazonClients(client, agent.account, agent.region, false)
    filter.includeTags = includeTags
    filter.excludeTags = excludeTags

    when:
    def result = agent.loadAutoScalingGroups(clients)

    then:
    result.asgs*.autoScalingGroupName() == expected

    where:
    includeTags                   | excludeTags                   | expected
    null                          | null                          | filterableASGs*.autoScalingGroupName()
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

  void "should get correct cache key pattern"() {
    given:
    def agent = getAgent()

    when:
    def cacheKeyPatterns = agent.getCacheKeyPatterns()

    then:
    cacheKeyPatterns.isPresent()
    cacheKeyPatterns.get() == [
      (SERVER_GROUPS.ns): "aws:serverGroups:*:accountName:region:*"
    ]
  }

  private static final List<AutoScalingGroup> filterableASGs = [
    AutoScalingGroup.builder()
      .autoScalingGroupName("test-hello-tag-value")
      .tags(TagDescription.builder().key("hello").value("goodbye").build())
      .build(),
    AutoScalingGroup.builder()
      .autoScalingGroupName("test-hello-tag-value-different")
      .tags(TagDescription.builder().key("hello").value("ciao").build())
      .build(),
    AutoScalingGroup.builder()
      .autoScalingGroupName("test-hello-tag-no-value")
      .tags(TagDescription.builder().key("hello").build())
      .build(),
    AutoScalingGroup.builder()
      .autoScalingGroupName("test-no-hello-tag")
      .tags(TagDescription.builder().key("Name").build())
      .build(),
    AutoScalingGroup.builder()
      .autoScalingGroupName("test-no-tags")
      .build(),
  ]

  private static def taggify(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.TagFilterOption(name, value)
  }

  private SuspendedProcess sP(String processName) {
    return SuspendedProcess.builder().processName(processName).build()
  }
}
