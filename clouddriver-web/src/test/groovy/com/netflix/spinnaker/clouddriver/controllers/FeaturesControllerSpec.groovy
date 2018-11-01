/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.elasticsearch.converters.UpsertEntityTagsAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class FeaturesControllerSpec extends Specification {
  def objectMapper = new ObjectMapper();

  void "should favor @Operation over @Component when determining stage names"() {
    given:
    def controller = new FeaturesController(
      atomicOperationConverters: [
        new UpsertEntityTagsAtomicOperationConverter(objectMapper, null, null, null, null), // @Component
        new UpsertMyCloudLoadBalancerAtomicOperationConverter() // @MyCloudOperation and @Component
      ]
    )

    when:
    def stages = controller.stages()

    then:
    stages == [
      [name: "upsertEntityTags", enabled: true],
      [name: "upsertLoadBalancer", enabled: true]
    ]
  }

  @MyCloudOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
  @Component("upsertMyCloudLoadBalancerDescription")
  class UpsertMyCloudLoadBalancerAtomicOperationConverter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      throw new UnsupportedOperationException()
    }

    @Override
    Object convertDescription(Map input) {
      throw new UnsupportedOperationException()
    }
  }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface MyCloudOperation {
  String value()
}