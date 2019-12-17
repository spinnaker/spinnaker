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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TemplatedPipelineModelMutatorSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()
  TemplateLoader templateLoader = Mock()
  Renderer renderer = Mock()

  @Subject
  TemplatedPipelineModelMutator subject = new TemplatedPipelineModelMutator(objectMapper, templateLoader, renderer)

  @Unroll
  def "should support templated pipelines only"() {
    expect:
    result == subject.supports(pipeline)

    where:
    pipeline                                 || result
    [stages: []]                             || false
    [type: 'templatedPipeline']              || false
    [type: 'templatedPipeline', config: [:]] || true
  }

  def "should not apply configurations from template if dynamically sourced"() {
    given:
    def pipeline = [
      config: [
        schema: '1',
        pipeline: [
          template: [
            source: '{{foo}}'
          ]
        ],
        stages: []
      ]
    ]

    when:
    subject.mutate(pipeline)

    then:
    0 * subject.applyConfigurationsFromTemplate(_, _, pipeline)
  }

  def "should merge expectedArtifacts when configured to inherit them"() {
    given:
    def pipeline = [
      config: [
        schema: '1',
        pipeline: [
          template: [
            source: 'static-template'
          ]
        ],
        configuration: [
          inherit: ['expectedArtifacts'],
          expectedArtifacts: [
            [
              id: 'artifact1'
            ] as NamedHashMap
          ]
        ]
      ]
    ]

    when:
    subject.mutate(pipeline)

    then:
    1 * templateLoader.load(_) >> { [new PipelineTemplate(
      schema: '1',
      configuration: new Configuration(
        expectedArtifacts: [
          [
            id: 'artifact2'
          ] as NamedHashMap
        ]
      )
    )]}
    pipeline.expectedArtifacts.size() == 2
  }

  def "should apply configurations from template if template is statically sourced"() {
    given:
    def pipeline = [
      config: [
        schema: '1',
        pipeline: [
          template: [
            source: 'static-template'
          ]
        ],
        configuration: [
          inherit: ['triggers', 'expectedArtifacts']
        ]
      ]
    ]

    when:
    subject.mutate(pipeline)

    then:
    1 * templateLoader.load(_) >> { [new PipelineTemplate(
      schema: '1',
      configuration: new Configuration(
        concurrentExecutions: [
          parallel: true
        ],
        triggers: [
          [
            name: 'package',
            type: 'jenkins',
            job: 'package-orca',
            master: 'spinnaker',
            enabled: true
          ] as NamedHashMap
        ],
        expectedArtifacts: [
          [
            type: 'jenkins/job'
          ] as NamedHashMap
        ],
        parameters: [
          [
            name: 'foo',
            description: 'blah'
          ] as NamedHashMap
        ]
      )
    )]}
    pipeline.triggers.size() == 1
    pipeline.expectedArtifacts.size() == 1
    pipeline.parameters == null
  }

  def "should map pipeline config id if it is unset"() {
    given:
    def pipeline = [
      id: 'pipeline-id',
      config: [
        schema: '1',
        pipeline: [
          template: [
            source: 'static-template'
          ]
        ]
      ]
    ]

    when:
    subject.mutate(pipeline)

    then:
    1 * templateLoader.load(_) >> { [new PipelineTemplate(
      schema: '1'
    )] }
    pipeline.id == pipeline.config.pipeline.pipelineConfigId
  }

  def "should map root-level configurations to mpt format if undefined"() {
    given:
    def pipeline = [
      id: 'id',
      name: 'my pipeline',
      application: 'orca',
      config: [
        schema: '1',
        pipeline: [
          template: [
            source: 'static-template'
          ]
        ]
      ]
    ]

    when:
    subject.mutate(pipeline)

    then:
    1 * templateLoader.load(_) >> { [new PipelineTemplate(
      schema: '1'
    )] }
    pipeline.name == pipeline.config.pipeline.name
    pipeline.application == pipeline.config.pipeline.application
  }
}
