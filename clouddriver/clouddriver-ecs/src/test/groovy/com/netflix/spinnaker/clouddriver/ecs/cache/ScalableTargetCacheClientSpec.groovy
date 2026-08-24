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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ScalableTargetCacheClient
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ScalableTargetsCachingAgent
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace
import software.amazon.awssdk.services.applicationautoscaling.model.SuspendedState
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS

class ScalableTargetCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  // mirrors clouddriver's ObjectMapper: AwsSdkV2Module is registered as a Spring Module bean
  def objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .registerModule(new AwsSdkV2Module())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  ScalableTargetCacheClient client = new ScalableTargetCacheClient(cacheView, objectMapper)

  def 'should convert cache data into object'() {
    given:
    def resourceId = 'service/test-cluster/test-service'
    def scalableTargetKey = Keys.getScalableTargetKey('test-account', 'us-west-1', resourceId)

    def givenScalableTarget = ScalableTarget.builder()
      .serviceNamespace(ServiceNamespace.ECS)
      .resourceId(resourceId)
      .scalableDimension("ecs:service:DesiredCount")
      .minCapacity(0)
      .maxCapacity(9001)
      .roleARN("role-arn")
      .creationTime(Instant.ofEpochMilli(1600000000000L))
      .suspendedState(SuspendedState.builder()
        .dynamicScalingInSuspended(false)
        .dynamicScalingOutSuspended(false)
        .scheduledScalingSuspended(false)
        .build())
      .build()

    // exercise the caching agent's write path, including the JSON round trip the cache performs
    def attributes = ScalableTargetsCachingAgent.convertScalableTargetToAttributes(givenScalableTarget, objectMapper)
    def cachedAttributes = objectMapper.readValue(objectMapper.writeValueAsString(attributes), Map)
    cacheView.get(SCALABLE_TARGETS.ns, scalableTargetKey) >> new DefaultCacheData(scalableTargetKey, cachedAttributes, [:])

    when:
    def retrievedScalableTarget = client.get(scalableTargetKey)

    then:
    retrievedScalableTarget == givenScalableTarget
  }
}
