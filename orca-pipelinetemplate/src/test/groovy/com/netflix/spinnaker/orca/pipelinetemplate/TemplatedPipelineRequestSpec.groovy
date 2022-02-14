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
package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import spock.lang.Specification
import spock.lang.Unroll

class TemplatedPipelineRequestSpec extends Specification {

  def objectMapper = new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(new KotlinModule.Builder().build())

  @Unroll
  def 'should deserialize config'() {
    when:
    objectMapper.convertValue(pipeline, TemplatedPipelineRequest.class)

    then:
    noExceptionThrown()

    where:
    pipeline << [
      [
        application: 'spindemo',
        name: 'test',
        stages: [
          type: 'wait',
          waitTime: 5
        ],
        limitConcurrent: true
      ],
      [
        application: 'spindemo',
        name: 'test',
        stages: [
          type: 'wait',
          waitTime: 5
        ],
        limitConcurrent: true,
        config: null
      ],
      [
        application: 'spindemo',
        name: 'test',
        stages: [
          type: 'wait',
          waitTime: 5
        ],
        limitConcurrent: true,
        config: [:]
      ]
    ]
  }

}
