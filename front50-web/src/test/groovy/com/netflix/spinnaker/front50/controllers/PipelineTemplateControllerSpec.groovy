/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.front50.model.pipeline.Pipeline.TYPE_TEMPLATED
import static com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration.TemplateSource.SPINNAKER_PREFIX

class PipelineTemplateControllerSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)
  def pipelineTemplateDAO = Mock(PipelineTemplateDAO)

  @Subject
  def controller = new PipelineTemplateController(
    pipelineDAO: pipelineDAO,
    pipelineTemplateDAO: pipelineTemplateDAO,
    objectMapper: new ObjectMapper(),
  )

  def "should reject delete request if template has dependent configs"() {
    given:
    def template = new PipelineTemplate(
      id: "myTemplate"
    )
    def pipeline = new Pipeline(
      type: TYPE_TEMPLATED,
      config: [
        pipeline: [
          template: [
            source: SPINNAKER_PREFIX + "myTemplate"
          ]
        ]
      ]
    )

    when:
    pipelineTemplateDAO.all() >> { [template] }
    pipelineDAO.all() >> { [pipeline] }
    controller.checkForDependentConfigs(template.getId(), true)

    then:
    thrown(InvalidRequestException)
  }

  def "should reject delete request if template has dependent templates"() {
    given:
    def templateId = "myTemplate"
    def pipelineTemplate = new PipelineTemplate(
      id: "myDependentTemplate",
      source: SPINNAKER_PREFIX + "myTemplate"
    )

    when:
    pipelineTemplateDAO.all() >> { [pipelineTemplate] }
    controller.checkForDependentTemplates(templateId)

    then:
    thrown(InvalidRequestException)
  }

  def "should recursively find dependent templates"() {
    given:
    def rootTemplate = new PipelineTemplate(
      id: 'rootTemplate'
    )
    def childTemplate = new PipelineTemplate(
      id: 'childTemplate',
      source: SPINNAKER_PREFIX + 'rootTemplate'
    )
    def grandchildTemplate = new PipelineTemplate(
      id: 'grandchildTemplate',
      source: SPINNAKER_PREFIX + 'childTemplate'
    )
    def unrelatedTemplate = new PipelineTemplate(
      id: 'unrelatedTemplate'
    )

    when:
    pipelineTemplateDAO.all() >> { [rootTemplate, childTemplate, grandchildTemplate, unrelatedTemplate] }
    def result = controller.getDependentTemplates('rootTemplate', Optional.empty())

    then:
    result == ['childTemplate', 'grandchildTemplate']
  }
}
