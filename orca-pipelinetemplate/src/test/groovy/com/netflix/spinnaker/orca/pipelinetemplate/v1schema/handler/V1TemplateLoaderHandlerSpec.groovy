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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest
import com.netflix.spinnaker.orca.pipelinetemplate.handler.DefaultHandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.GlobalPipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Subject

class V1TemplateLoaderHandlerSpec extends Specification {

  Renderer renderer = Mock() {
    renderGraph(_, _) >> { value, _ ->
      return value
    }
  }

  @Subject
  def subject = new V1TemplateLoaderHandler(Mock(TemplateLoader), renderer, new ObjectMapper())

  def 'should create stub template when no template is provided'() {
    given:
    def configuration = [
      schema: '1',
      pipeline: [
        application: 'orca',
        name: 'My Template',
        variables: [
          foo: 'bar'
        ]
      ],
      stages: [
        [
          id: 'wait',
          type: 'wait',
          config: [
            waitTime: 5
          ]
        ]
      ]
    ]

    and:
    def chain = new DefaultHandlerChain()
    def context = new GlobalPipelineTemplateContext(chain, new TemplatedPipelineRequest(
      config: configuration
    ))

    when:
    subject.handle(chain, context)

    then:
    noExceptionThrown()
    context.schemaContext != null
    (context.schemaContext as V1PipelineTemplateContext).template.variables*.name == ['foo']
    (context.schemaContext as V1PipelineTemplateContext).template.variables*.defaultValue == ['bar']

  }
}
