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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import spock.lang.Specification
import spock.lang.Unroll

class V1SchemaValidationHelperSpec extends Specification {

  @Unroll
  def "should validate stage definitions"() {
    given:
    Errors errors = new Errors()

    when:
    V1SchemaValidationHelper.validateStageDefinitions([stageDefinition], errors, { return "namespace:$it" as String })

    then:
    if (expectedError) {
      errors.errors[0] == expectedError
    } else {
      !errors.hasErrors(true)
    }

    where:
    stageDefinition << [
      new StageDefinition(),
      new StageDefinition(id: 'foo'),
      new StageDefinition(id: 'foo', type: 'findImage'),
      new StageDefinition(id: 'foo', type: 'findImage', config: [:]),
      new StageDefinition(id: 'foo', type: 'findImage', config: [:], dependsOn: ['bar'], inject: [first: true]),
      new StageDefinition(id: 'foo', type: 'findImage', config: [:], inject: [first: true, last: true])
    ]
    expectedError << [
      new Errors.Error(message: 'Stage ID is unset', location: 'namespace:stages'),
      new Errors.Error(message: 'Stage is missing type', location: 'namespace:stages.foo'),
      new Errors.Error(message: 'Stage configuration is unset', location: 'namespace:stages.foo'),
      false,
      new Errors.Error(message: 'A stage cannot have both dependsOn and an inject rule defined simultaneously', location: 'namespace:stages.foo'),
      new Errors.Error(message: 'A stage cannot have multiple inject rules defined', location: 'namespace:stages.foo')
    ]
  }

  static String locationFormatter(String location) {
    return "namespace:$location" as String
  }
}
