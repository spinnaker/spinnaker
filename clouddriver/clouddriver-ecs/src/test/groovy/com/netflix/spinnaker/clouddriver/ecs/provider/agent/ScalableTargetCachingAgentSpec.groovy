/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS

class ScalableTargetCachingAgentSpec extends Specification {
  def autoscaling = Mock(ApplicationAutoScalingClient)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  ScalableTargetsCachingAgent agent = new ScalableTargetsCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider, objectMapper)

  def 'should get a list of scalable targets'() {
    given:
    def givenScalableTargets = (0..4).collect {
      ScalableTarget.builder()
        .serviceNamespace(ServiceNamespace.ECS)
        .resourceId("service:/test-cluster/test-service-v00${it}")
        .scalableDimension("ecs:service:DesiredCount")
        .minCapacity(0)
        .maxCapacity(9001)
        .roleARN("role-arn")
        .creationTime(Instant.now())
        .build()
    }
    autoscaling.describeScalableTargets(_ as DescribeScalableTargetsRequest) >>
      DescribeScalableTargetsResponse.builder().scalableTargets(givenScalableTargets).build()

    when:
    def retrievedScalableTargets = agent.fetchScalableTargets(autoscaling)

    then:
    retrievedScalableTargets.containsAll(givenScalableTargets)
    givenScalableTargets.containsAll(retrievedScalableTargets)
  }

  def 'should generate fresh data'() {
    given:
    Set givenScalableTargets = (0..4).collect {
      ScalableTarget.builder()
        .serviceNamespace(ServiceNamespace.ECS)
        .resourceId("service:/test-cluster/test-service-v00${it}")
        .scalableDimension("ecs:service:DesiredCount")
        .minCapacity(0)
        .maxCapacity(9001)
        .roleARN("role-arn")
        .build()
    }.toSet()

    when:
    def cacheData = agent.generateFreshData(givenScalableTargets)

    then:
    cacheData.size() == 1
    cacheData.get(SCALABLE_TARGETS.ns).size() == givenScalableTargets.size()
    givenScalableTargets*.resourceId().containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().resourceId)
    givenScalableTargets*.minCapacity().containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().minCapacity)
    givenScalableTargets*.maxCapacity().containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().maxCapacity)
    givenScalableTargets*.roleARN().containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().roleARN)
  }

  def 'should use filterIdentifiers with account and region glob for evictions'() {
    given:
    def givenScalableTarget = ScalableTarget.builder()
      .serviceNamespace(ServiceNamespace.ECS)
      .resourceId("service:/test-cluster/test-service-v001")
      .scalableDimension("ecs:service:DesiredCount")
      .minCapacity(0)
      .maxCapacity(9001)
      .roleARN("role-arn")
      .build()
    clientProvider.getAmazonApplicationAutoScalingV2(_, _) >> autoscaling
    autoscaling.describeScalableTargets(_ as DescribeScalableTargetsRequest) >>
      DescribeScalableTargetsResponse.builder().scalableTargets([givenScalableTarget]).build()

    def account = 'test-account'
    def region = 'us-west-1'
    def expectedGlob = com.netflix.spinnaker.clouddriver.ecs.cache.Keys.buildGlob(SCALABLE_TARGETS, account, region)
    def oldIdentifiers = ['ecs;scalable-targets;test-account;us-west-1;old-target']
    providerCache.filterIdentifiers(SCALABLE_TARGETS.ns, expectedGlob) >> oldIdentifiers

    when:
    def result = agent.loadData(providerCache)

    then:
    result.evictions[SCALABLE_TARGETS.ns] != null
    result.evictions[SCALABLE_TARGETS.ns].containsAll(oldIdentifiers)
  }
}
