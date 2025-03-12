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

import com.amazonaws.services.applicationautoscaling.model.ScalableTarget
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ScalableTargetCacheClient
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS

class ScalableTargetCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  ScalableTargetCacheClient client = new ScalableTargetCacheClient(cacheView, objectMapper)

  def 'should convert cache data into object'() {
    given:
    def resourceId = 'service/test-cluster/test-service'
    def scalableTargetKey = Keys.getScalableTargetKey('test-account', 'us-west-1', resourceId)

    def givenScalableTarget = new ScalableTarget(
      serviceNamespace: ServiceNamespace.Ecs,
      resourceId: resourceId,
      scalableDimension: 'scalable-dimension',
      minCapacity: 0,
      maxCapacity: 9001,
      roleARN: 'role-arn',
      creationTime: new Date()
    )

    objectMapper

    def attributes = objectMapper.convertValue(givenScalableTarget, Map)
    cacheView.get(SCALABLE_TARGETS.ns, scalableTargetKey) >> new DefaultCacheData(scalableTargetKey, attributes, [:])

    when:
    def retrievedScalableTarget = client.get(scalableTargetKey)

    then:
    retrievedScalableTarget == givenScalableTarget
  }
}
