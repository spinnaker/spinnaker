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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HandlebarsRendererSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject Renderer subject = new HandlebarsRenderer(objectMapper)

  @Unroll
  def 'should render and return correct java type'() {
    given:
    RenderContext context = new RenderContext('myApp', new PipelineTemplate(), [job: 'job', buildNumber: 1234 ]).with {
      put('stringVar', 'myStringValue')
      put('regions', ['us-east-1', 'us-west-2'])
      put('objectVar', [key1: 'value1', key2: 'value2'])
      return it
    }

    when:
    def result = subject.renderGraph(template, context)

    then:
    expectedType.isAssignableFrom(result.getClass())
    result == expectedResult

    where:
    template              || expectedType   | expectedResult
    '1'                   || Integer        | 1
    '1.1'                 || Double         | 1.1
    'true'                || Boolean        | true
    '{{ stringVar }}'     || String         | 'myStringValue'
    '''

[
  {{#each regions}}
  "{{ this }}"{{#unless @last}},{{/unless}}
  {{/each}}
]
'''                       || List           | ['us-east-1', 'us-west-2']
    '{{json objectVar}}'  || Map            | [key1: 'value1', key2: 'value2']
    '{{json trigger}}'    || Map            | [job: 'job', buildNumber: 1234]
    '{{unknownVar}}'      || String         | '{{unknownVar}}'
  }
}
