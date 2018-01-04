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
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class JinjaRendererSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  RenderedValueConverter renderedValueConverter = new YamlRenderedValueConverter(new Yaml())

  @Subject
  Renderer subject = new JinjaRenderer(renderedValueConverter, objectMapper, Mock(Front50Service), [])

  @Unroll
  def 'should render and return correct java type'() {
    given:
    RenderContext context = new DefaultRenderContext('myApp', new PipelineTemplate(), [job: 'job', buildNumber: 1234]).with {
      variables.put('stringVar', 'myStringValue')
      variables.put('regions', ['us-east-1', 'us-west-2'])
      variables.put('objectVar', [key1: 'value1', key2: 'value2'])
      variables.put('isDebugNeeded', false)
      it
    }

    when:
    def result = subject.renderGraph(template, context)

    then:
    if (result != null) {
      expectedType.isAssignableFrom(result.getClass())
    }
    result == expectedResult

    where:
    template          || expectedType | expectedResult
    '1'               || Integer      | 1
    '1.1'             || Double       | 1.1
    'true'            || Boolean      | true
    '{{ stringVar }}' || String       | 'myStringValue'
    'yes'             || String       | 'yes'
    'on'              || String       | 'on'
    '''

[
  {% for region in regions %}
    "{{ region }}"{% if not loop.last %},{% endif %}
  {% endfor %}
]
'''                   || List         | ['us-east-1', 'us-west-2']
    '''\
[
{% for key, value in objectVar.items() %}
  "{{ key }}:{{ value }}"{% if not loop.last %},{% endif %}
{% endfor %}
]
'''                   || List         | ['key1:value1', 'key2:value2']
    '''
{% for region in regions %}
- {{ region }}
{% endfor %}
'''                   || List         | ['us-east-1', 'us-west-2']
    '''
"${ {{isDebugNeeded}} }".equalsIgnoreCase("True")
'''                   || String       | '"${ false }".equalsIgnoreCase("True")'
    '#stage("First Wait")["status"].toString() == "SUCCESS"' || String | '#stage("First Wait")["status"].toString() == "SUCCESS"'
    '${ #stage("First Wait")["status"].toString() == "SUCCESS" }' || String | '${ #stage("First Wait")["status"].toString() == "SUCCESS" }'
    '${ parameters.CONFIG_FOLDER ?: \'\' }' || String | '${ parameters.CONFIG_FOLDER ?: \'\' }'
    ''                || String       | null
    '* markdown list' || String       | '* markdown list'
    'noexpand:{"t": "deployment"}' || String   | '{"t": "deployment"}'
  }

  def 'should render nullable field as null'() {
    given:
    RenderContext context = new DefaultRenderContext('myApp', new PipelineTemplate(), [job: 'job', buildNumber: 1234]).with {
      variables.put('nullableVar', null) // nullable variables
      it
    }

    when:
    def result = subject.renderGraph('{{ nullableVar }}', context)

    then:
    result == null
  }

  def 'should throw exception on missing variable values if even given nullable variables'() {
    given:
    RenderContext context = new DefaultRenderContext('myApp', new PipelineTemplate(), [job: 'job', buildNumber: 1234]).with {
      variables.put('nullableVar', null) // nullable variables
      it
    }

    when:
    subject.renderGraph('{{ missingVar }}', context)

    then:
    TemplateRenderException fte = thrown()
    fte.message == 'failed rendering jinja template'
  }

  def 'should throw exception on missing variable values without any nullable variables'() {
    given:
    RenderContext context = new DefaultRenderContext('myApp', new PipelineTemplate(), [job: 'job', buildNumber: 1234]).with {
      it
    }

    when:
    subject.renderGraph('{{ missingVar }}', context)

    then:
    TemplateRenderException tre = thrown()
    tre.message == 'failed rendering jinja template'
  }
}
