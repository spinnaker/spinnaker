/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PipelineIdTagSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()
  Front50Service front50Service = Mock(Front50Service)
  Renderer renderer = new JinjaRenderer(objectMapper, front50Service, [])

  @Subject
  PipelineIdTag subject = new PipelineIdTag(front50Service)

  @Unroll
  def 'should render pipeline id'() {
    given:
    front50Service.getPipelines('myApp', false) >>  Calls.response([
      [
        name: 'Bake and Tag',
        application: 'myApp',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ],
      [
        name: 'Important pipeline',
        application: 'myApp',
        id: '1685429e-beb1-4d35-963c-02b9a01977e1',
        stages: []
      ],
      [
        name: 'pipe in different app',
        application: 'testApp',
        id: '1685429e-beb1-4d35-963c-02b9a01977e1',
        stages: []
      ],
      [
        name: "Rob's great pipeline",
        application: 'myApp',
        id: '1685429e-beb1-4d35-963c-123456789012',
        stages: []
      ]
    ])

    expect:
    renderer.render(
      tag,
      new DefaultRenderContext('myApp',null, [:])
    ) == expectedId

    where:
    tag                                                      || expectedId
    '{% pipelineId application=myApp name="Bake and Tag" %}' || '9595429f-afa0-4c34-852b-01a9a01967f9'
    "{% pipelineId name='Bake and Tag' %}"                   || '9595429f-afa0-4c34-852b-01a9a01967f9'
    '{% pipelineId name="Rob\'s great pipeline" %}'          || '1685429e-beb1-4d35-963c-123456789012'
  }

  def 'should render pipeline id using variables defined in context'() {
    given:
    front50Service.getPipelines('myApp', false) >> Calls.response([
      [
        name: 'Bake and Tag',
        application: 'myApp',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ]
    ])

    RenderContext context = new DefaultRenderContext('myApp', null, [:])
    context.variables.put("pipelineName", "Bake and Tag")
    context.variables.put("applicationName", "myApp")

    expect:
    renderer.render('{% pipelineId application=applicationName name=pipelineName %}', context) ==  '9595429f-afa0-4c34-852b-01a9a01967f9'
  }

    def 'should handle missing input params'() {
    given: 'a pipelineId tag with no app defined'
    def applicationInContext = 'myApp'
    def context = new DefaultRenderContext(applicationInContext,null, [:])

    when:
    renderer.render('{% pipelineId name="Bake and Tag" %}', context)

    then: 'application should be inferred from context'
    1 * front50Service.getPipelines(applicationInContext, false) >>  Calls.response([
      [
        name: 'Bake and Tag',
        application: 'myApp',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ]
    ])

    when: 'template is missing required fields (name)'
    renderer.render('{% pipelineId application=myApp %}', context)

    then:
    thrown(TemplateRenderException)

    when: 'template is missing required fields (all)'
    renderer.render('{% pipelineId %}', context)

    then:
    thrown(TemplateRenderException)

    when: 'no pipeline was not found from provided input'
    renderer.render('{% pipelineId name="Bake and Tag" %}', context)

    then:
    1 * front50Service.getPipelines(applicationInContext, false) >>  Calls.response([])
    thrown(TemplateRenderException)
  }
}
