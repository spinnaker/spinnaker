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

package com.netflix.spinnaker.clouddriver.ecs.cache

import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealth
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthStateEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TargetHealthCachingAgent
import com.netflix.spinnaker.cats.cache.DefaultCacheData

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TARGET_HEALTHS;

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TargetHealthCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsTargetHealth
import spock.lang.Specification
import spock.lang.Subject

class TargetHealthCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  // mirrors clouddriver's ObjectMapper: AwsSdkV2Module is registered as a Spring Module bean
  def objectMapper = new ObjectMapper().registerModule(new AwsSdkV2Module())
  @Subject
  private TargetHealthCacheClient client = new TargetHealthCacheClient(cacheView, objectMapper)

  def 'should convert'() {
    given:
    def targetId = '10.0.0.13'
    def targetGroupArn = 'arn:targetgroup'
    def key =
      Keys.getTargetHealthKey('test-account', 'us-west-1', targetGroupArn)

    def targetHealthDescription = TargetHealthDescription.builder()
      .target(TargetDescription.builder().id(targetId).port(80).build())
      .targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.HEALTHY).build())
      .build()

    def originalTargetHealth = new EcsTargetHealth(
      targetGroupArn: targetGroupArn,
      targetHealthDescriptions: Collections.singletonList(targetHealthDescription)
    )

    // exercise the caching agent's write path, including the JSON round trip the cache performs
    def attributes = TargetHealthCachingAgent.convertToTargetHealthAttributes(originalTargetHealth)
    def cachedAttributes = objectMapper.readValue(objectMapper.writeValueAsString(attributes), Map)
    cacheView.get(TARGET_HEALTHS.toString(), key) >> new DefaultCacheData(key, cachedAttributes, Collections.emptyMap())

    when:
    def retrievedTargetHealth = client.get(key)

    then:
    retrievedTargetHealth == originalTargetHealth
  }
}
