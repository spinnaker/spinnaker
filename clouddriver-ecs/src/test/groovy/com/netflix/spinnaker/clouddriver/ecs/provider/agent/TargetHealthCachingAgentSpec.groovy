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

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealth
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthStateEnum
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
  def ecs = Mock(AmazonECS)
  def clientProvider = Mock(AmazonClientProvider)
  def awsProviderCache = Mock(ProviderCache)
  def credentialsProvider = Mock(AWSCredentialsProvider)
  def amazonloadBalancing = Mock(AmazonElasticLoadBalancing)
  def targetGroupArn = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':123456789012:targetgroup/test-tg/9e8997b7cff00c62'
  ObjectMapper mapper = new ObjectMapper()

  @Subject
  TargetHealthCachingAgent agent =
    new TargetHealthCachingAgent(CommonCachingAgent.netflixAmazonCredentials, CommonCachingAgent.REGION, clientProvider, credentialsProvider, mapper)

  def setup() {
    clientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> amazonloadBalancing
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
      request.targetGroupArn == targetGroupArn
    }) >> new DescribeTargetHealthResult()

    targetHealthList.size() == 0
  }

  def 'should get a list of target health objects'() {
    given:
    def healthyTargetId = '10.0.0.3'
    def unhealthyTargetId = '10.0.0.14'

    TargetHealthDescription targetHealth1 =
      new TargetHealthDescription().withTarget(
        new TargetDescription().withId(healthyTargetId).withPort(80))
      .withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))

    TargetHealthDescription targetHealth2 =
      new TargetHealthDescription().withTarget(
        new TargetDescription().withId(unhealthyTargetId).withPort(80))
      .withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Unhealthy))

    when:
    agent.setAwsCache(awsProviderCache)
    def targetHealthList = agent.getItems(ecs, Mock(ProviderCache))

    then:
    1 * amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn == targetGroupArn
    }) >> new DescribeTargetHealthResult().withTargetHealthDescriptions(targetHealth1, targetHealth2)

    targetHealthList.size() == 1
    EcsTargetHealth targetHealth = targetHealthList.get(0)
    targetHealth.getTargetGroupArn() == targetGroupArn
    for (TargetHealthDescription targetHealthDescription: targetHealth.getTargetHealthDescriptions()) {
      targetHealthDescription.getTarget().getPort() == 80
      if (targetHealthDescription.getTarget().getId() == healthyTargetId) {
        targetHealthDescription.getTargetHealth().getState() == TargetHealthStateEnum.Healthy.toString()
      } else {
        targetHealthDescription.getTargetHealth().getState() == TargetHealthStateEnum.Unhealthy.toString()
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
      request.targetGroupArn == targetGroupArn
    }) >> { throw new TargetGroupNotFoundException("The specified target group does not exist.") }

    targetHealthList.size() == 0
  }
}
