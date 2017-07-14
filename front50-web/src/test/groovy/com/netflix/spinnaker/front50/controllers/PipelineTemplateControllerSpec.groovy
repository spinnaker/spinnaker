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
    def templateId = "myTemplate"
    def pipeline = new Pipeline(
      type: "templatedPipeline",
      config: [
        pipeline: [
          template: [
            source: "spinnaker://myTemplate"
          ]
        ]
      ]
    )

    when:
    pipelineDAO.all() >> { [pipeline] }
    controller.checkForDependentConfigs(templateId)

    then:
    thrown(InvalidRequestException)
  }

  def "should reject delete request if template has dependent templates"() {
    given:
    def templateId = "myTemplate"
    def pipelineTemplate = new PipelineTemplate(
      id: "myDependentTemplate",
      source: "spinnaker://myTemplate"
    )

    when:
    pipelineTemplateDAO.all() >> { [pipelineTemplate] }
    controller.checkForDependentTemplates(templateId)

    then:
    thrown(InvalidRequestException)
  }
}
