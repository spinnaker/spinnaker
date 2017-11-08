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
package com.netflix.spinnaker.orca.pipelinetemplate.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplatePreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import spock.lang.Specification
import spock.lang.Subject

class PlanTemplateDependentsTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  PipelineTemplatePreprocessor pipelinePreprocessor = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  def task = new PlanTemplateDependentsTask(
    front50Service: front50Service,
    pipelineTemplateObjectMapper: objectMapper,
    pipelineTemplatePreprocessor: pipelinePreprocessor
  )

  def 'should aggregate all failed pipeline plan errors'() {
    given:
    def pipelineTemplate = new PipelineTemplate(
      schema: "1",
      id: 'myTemplate',
      metadata: [
        name: 'myTemplate'
      ],
      variables: []
    )

    def pipeline1 = [
      id: 'one',
      config: [:]
    ]
    def pipeline2 = [
      id: 'two',
      config: [:]
    ]

    when:
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "", [
      pipelineTemplate: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipelineTemplate).bytes)
    ]))

    then:
    noExceptionThrown()
    1 * front50Service.getPipelineTemplateDependents('myTemplate', false) >> {
      [pipeline1, pipeline2]
    }
    2 * pipelinePreprocessor.process(_) >> {
      [
        errors: [
          [
            severity: 'FATAL',
            message: 'Some error'
          ]
        ]
      ]
    }
    result.status == ExecutionStatus.TERMINAL
    result.context['notification.type'] == 'plantemplatedependents'
    result.context['pipelineTemplate.id'] == 'myTemplate'
    result.context['pipelineTemplate.allDependentPipelines'] == ['one', 'two']
    result.context['pipelineTemplate.dependentErrors'] == [
      one: [
        [
          severity: 'FATAL',
          message: 'Some error'
        ]
      ],
      two: [
        [
          severity: 'FATAL',
          message: 'Some error'
        ]
      ]
    ]
  }

  Map<String, Object> buildProcessRequest(Map<String, Object> pipeline, PipelineTemplate template) {
    return [
      type: 'templatedPipeline',
      trigger: [:],
      config: pipeline.get("config"),
      template: template,
      plan: true
    ]
  }
}
