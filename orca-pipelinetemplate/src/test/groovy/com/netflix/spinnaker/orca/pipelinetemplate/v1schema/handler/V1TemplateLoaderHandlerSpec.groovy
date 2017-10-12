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
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest
import com.netflix.spinnaker.orca.pipelinetemplate.handler.DefaultHandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.GlobalPipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.FileTemplateSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class V1TemplateLoaderHandlerSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  Renderer renderer = new JinjaRenderer(objectMapper, Mock(Front50Service), [])

  TemplateLoader templateLoader = new TemplateLoader([new FileTemplateSchemeLoader(objectMapper)])

  @Subject
  def subject = new V1TemplateLoaderHandler(templateLoader, renderer, objectMapper)

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

  @Unroll
  def 'should render jinja expressions contained within template variables'() {
    given:
    def pipelineTemplate = new PipelineTemplate(variables: templateVariables.collect {
      new PipelineTemplate.Variable(name: it.key, defaultValue: it.value)
    })

    def templateConfig = new TemplateConfiguration(
      pipeline: new TemplateConfiguration.PipelineDefinition(variables: configVariables)
    )

    def renderContext = RenderUtil.createDefaultRenderContext(
      pipelineTemplate, templateConfig, [
      parameters: [
        "list"   : "us-west-2,us-east-1",
        "boolean": "true",
        "string" : "this is a string"
      ]
    ])

    when:
    subject.renderTemplateVariables(renderContext, pipelineTemplate)

    then:
    pipelineTemplate.variables*.defaultValue == expectedDefaultValues

    where:
    templateVariables                                                     | configVariables       || expectedDefaultValues
    [key1: "string1", key2: "string2"]                                    | [:]                   || ["string1", "string2"]
    [key1: "{{ trigger.parameters.string }}", key2: "string2"]            | [:]                   || ["this is a string", "string2"]
    [key1: "string1", key2: "{{ key1 }}"]                                 | [:]                   || ["string1", "string1"]
    [key2: "{{ key1 }}"]                                                  | [key1: "string1"]     || ["string1"]
  }

  @Unroll
  def 'should be able to set source using jinja'() {
    given:
    def chain = new DefaultHandlerChain()
    def context = new GlobalPipelineTemplateContext(chain, createInjectedTemplateRequest(template))

    when:
    subject.handle(chain, context)

    then:
    ((V1PipelineTemplateContext) context.getSchemaContext()).template.stages*.name == expectedStageNames

    where:
    template        || expectedStageNames
    'jinja-001.yml' || ['jinja1']
    'jinja-002.yml' || ['jinja2']
  }

  def 'should allow inlined templates during plan'() {
    given:
    def chain = new DefaultHandlerChain()
    def context = new GlobalPipelineTemplateContext(chain, createInlinedTemplateRequest(true))

    when:
    subject.handle(chain, context)

    then:
    noExceptionThrown()
    ((V1PipelineTemplateContext) context.getSchemaContext()).template.stages*.name == ['wait']
  }

  def 'should load parent templates of inlined template during plan'() {
    given:
    def chain = new DefaultHandlerChain()
    def context = new GlobalPipelineTemplateContext(chain, createInlinedTemplateRequestWithParent(true, 'jinja-001.yml'))

    when:
    subject.handle(chain, context)

    then:
    noExceptionThrown()
    ((V1PipelineTemplateContext) context.getSchemaContext()).template.stages*.name == ['jinja1', 'childTemplateWait']
  }

  TemplatedPipelineRequest createInjectedTemplateRequest(String templatePath) {
    return new TemplatedPipelineRequest(
      type: 'templatedPipeline',
      trigger: [
        parameters: [
          template: getClass().getResource("/templates/${templatePath}").toURI()
        ]
      ],
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp',
          template: [
            source: '{{trigger.parameters.template}}'
          ],
        ],
      ],
      plan: false
    )
  }

  TemplatedPipelineRequest createInlinedTemplateRequest(boolean plan) {
    return new TemplatedPipelineRequest(
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        stages: [
          [
            id: 'wait',
            type: 'wait',
            config: [
              waitTime: 5
            ]
          ]
        ]
      ],
      plan: plan
    )
  }

  TemplatedPipelineRequest createInlinedTemplateRequestWithParent(boolean plan, String templatePath) {
    return new TemplatedPipelineRequest(
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        stages: [
          [
            id: 'childTemplateWait',
            type: 'wait',
            config: [
              waitTime: 5
            ],
            inject: [
              last: true
            ]
          ]
        ],
        source: getClass().getResource("/templates/${templatePath}").toURI()
      ],
      plan: plan
    )
  }
}
