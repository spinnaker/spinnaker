/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealth
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthStateEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsTargetHealth
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS
import spock.lang.Specification
import spock.lang.Subject

class TargetHealthCachingAgentSpec extends Specification {
  def ecs = Mock(EcsClient)
  def clientProvider = Mock(AmazonClientProvider)
  def awsProviderCache = Mock(ProviderCache)
  def amazonloadBalancing = Mock(ElasticLoadBalancingV2Client)
  def targetGroupArn = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':' + CommonCachingAgent.ACCOUNT_ID + ':targetgroup/test-tg/9e8997b7cff00c62'
  ObjectMapper mapper = new ObjectMapper()

  @Subject
  TargetHealthCachingAgent agent =
    new TargetHealthCachingAgent(CommonCachingAgent.netflixAmazonCredentials, CommonCachingAgent.REGION, clientProvider, mapper)

  def setup() {
    clientProvider.getAmazonElasticLoadBalancingV2V2(_, _) >> amazonloadBalancing
    awsProviderCache.filterIdentifiers(_, _) >> []

    def targetGroupAttributes = [
      loadBalancerNames: ['loadBalancerName'],
      targetGroupArn: targetGroupArn,
      targetGroupName: 'test-tg',
      vpcId: 'vpc-id',
    ]
    def targetGroupKey =
      Keys.getTargetGroupKey('test-tg', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'ip', 'vpc-id')
    def loadbalancerKey =
      Keys.getLoadBalancerKey('loadBalancerName', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'vpc-id', 'ip')
    def relations = [loadBalancers: [loadbalancerKey]]
    def targetGroupCacheData =
      new DefaultCacheData(targetGroupKey, targetGroupAttributes, relations)

    awsProviderCache.getAll(TARGET_GROUPS.getNs(), _, _) >> Collections.singletonList(targetGroupCacheData)
  }

  def 'should skip targetGroups with empty TargetHealthDescriptions'() {
    when:
    agent.setAwsCache(awsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    // ELB response contains no TargetHealths
    1 * amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn() == targetGroupArn
    }) >> DescribeTargetHealthResponse.builder().build()

    targetHealthList.size() == 0
  }

  def 'should get a list of target health objects'() {
    given:
    def healthyTargetId = '10.0.0.3'
    def unhealthyTargetId = '10.0.0.14'

    TargetHealthDescription targetHealth1 =
      TargetHealthDescription.builder()
        .target(TargetDescription.builder().id(healthyTargetId).port(80).build())
        .targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.HEALTHY).build())
        .build()

    TargetHealthDescription targetHealth2 =
      TargetHealthDescription.builder()
        .target(TargetDescription.builder().id(unhealthyTargetId).port(80).build())
        .targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.UNHEALTHY).build())
        .build()

    when:
    agent.setAwsCache(awsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    1 * amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn() == targetGroupArn
    }) >> DescribeTargetHealthResponse.builder().targetHealthDescriptions(targetHealth1, targetHealth2).build()

    targetHealthList.size() == 1
    EcsTargetHealth targetHealth = targetHealthList.get(0)
    targetHealth.getTargetGroupArn() == targetGroupArn
    for (TargetHealthDescription targetHealthDescription: targetHealth.getTargetHealthDescriptions()) {
      targetHealthDescription.target().port() == 80
      if (targetHealthDescription.target().id() == healthyTargetId) {
        targetHealthDescription.targetHealth().stateAsString() == TargetHealthStateEnum.HEALTHY.toString()
      } else {
        targetHealthDescription.targetHealth().stateAsString() == TargetHealthStateEnum.UNHEALTHY.toString()
      }
    }
  }

  def 'should raise exception if getItems() called before awsProviderCache is set'() {
    when:
    agent.getItems(ecs, Mock(ProviderCache))

    then:
    thrown NullPointerException
  }

  def 'should catch and ignore TargetGroupNotFoundExceptions'() {
    when:
    agent.setAwsCache(awsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    1 * amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn() == targetGroupArn
    }) >> { throw TargetGroupNotFoundException.builder().message("The specified target group does not exist.").build() }

    targetHealthList.size() == 0
  }

  def 'should describe and return target groups for matching account only'() {
    given:
    // set up correct account cache data
    def targetGroupAttributes = [
      loadBalancerNames: ['loadBalancerName'],
      targetGroupArn: targetGroupArn,
      targetGroupName: 'test-tg',
      vpcId: 'vpc-id',
    ]
    def targetGroupKey =
      Keys.getTargetGroupKey('test-tg', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'ip', 'vpc-id')
    def loadbalancerKey =
      Keys.getLoadBalancerKey('loadBalancerName', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'vpc-id', 'ip')
    def relations = [loadBalancers: [loadbalancerKey]]
    def targetGroupCacheData =
      new DefaultCacheData(targetGroupKey, targetGroupAttributes, relations)

    // set up other account cache data
    def targetGroupArn2 = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':210987654321:targetgroup/other-tg/7uf491b7cff00c62'
    def otherAccountName = "other-account"
    def targetGroupAttributes2 = [
      loadBalancerNames: ['loadBalancerName'],
      targetGroupArn: targetGroupArn2,
      targetGroupName: 'other-tg',
      vpcId: 'vpc-id',
    ]
    def targetGroupKey2 =
      Keys.getTargetGroupKey('test-tg', otherAccountName, CommonCachingAgent.REGION, 'ip', 'vpc-id')
    def loadbalancerKey2 =
      Keys.getLoadBalancerKey('loadBalancerName', otherAccountName, CommonCachingAgent.REGION, 'vpc-id', 'ip')
    def relations2 = [loadBalancers: [loadbalancerKey2]]
    def targetGroupCacheData2 =
      new DefaultCacheData(targetGroupKey2, targetGroupAttributes2, relations2)

    TargetHealthDescription targetHealth =
      TargetHealthDescription.builder()
        .target(TargetDescription.builder().id('10.0.0.3').port(80).build())
        .targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.HEALTHY).build())
        .build()

    // return cache data for both target groups
    awsProviderCache.getAll(TARGET_GROUPS.getNs(), _, _) >> [targetGroupCacheData, targetGroupCacheData2]

    when:
    agent.setAwsCache(awsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    // expect one describe call, with correct target group
    1 * amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn() == targetGroupArn
    }) >> DescribeTargetHealthResponse.builder().targetHealthDescriptions(targetHealth).build()

    targetHealthList.size() == 1
    EcsTargetHealth targetHealthDescription = targetHealthList.get(0)
    targetHealthDescription.getTargetGroupArn() == targetGroupArn
  }

  def 'should handle null targetGroupArn in cache data'() {
    given:
    def targetGroupAttributes = [
      loadBalancerNames: ['loadBalancerName'],
      targetGroupArn: null,
      targetGroupName: null,
      vpcId: 'vpc-id',
    ]
    def targetGroupKey =
      Keys.getTargetGroupKey('test-tg', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'ip', 'vpc-id')
    def loadbalancerKey =
      Keys.getLoadBalancerKey('loadBalancerName', CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, 'vpc-id', 'ip')
    def relations = [loadBalancers: [loadbalancerKey]]
    def targetGroupCacheData =
      new DefaultCacheData(targetGroupKey, targetGroupAttributes, relations)


    def badAwsProviderCache = Mock(ProviderCache)
    badAwsProviderCache.filterIdentifiers(_, _) >> []
    badAwsProviderCache.getAll(TARGET_GROUPS.getNs(), _, _) >> [targetGroupCacheData]

    when:
    agent.setAwsCache(badAwsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    targetHealthList.size() == 0
  }

  def 'should return empty set when no target group cache data available'() {
    given:
    def emptyAwsProviderCache = Mock(ProviderCache)
    emptyAwsProviderCache.filterIdentifiers(_, _) >> []
    emptyAwsProviderCache.getAll(TARGET_GROUPS.getNs(), _, _) >> []

    when:
    agent.setAwsCache(emptyAwsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    targetHealthList.size() == 0
  }
}
