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
package com.netflix.spinnaker.gate.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.services.PipelineTemplateService
import com.netflix.spinnaker.gate.services.TaskService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import javax.ws.rs.core.MediaType

class PipelineTemplateControllerSpec extends Specification {

  @Unroll
  def "should inflect job application from pipeline template metadata"() {
    given:
    def pipelineTemplateService = Mock(PipelineTemplateService)
    def taskService = Mock(TaskService)
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PipelineTemplatesController(pipelineTemplateService, taskService, new ObjectMapper())).build()

    and:
    def pipelineTemplate = [
      id: 'foo',
      metadata: metadata,
      configuration: [:]
    ]

    when:
    def response = mockMvc.perform(
      post("/pipelineTemplates").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipelineTemplate))
    ).andReturn().response

    then:
    response.status == 202
    1 * taskService.create([
      application: app,
      description: "Create pipeline template 'foo'",
      job: [
        [
          type: 'createPipelineTemplate',
          pipelineTemplate: [
            id: 'foo',
            metadata: metadata,
            configuration: [:]
          ]
        ]
      ]
    ]) >> { [ref: 'taskref'] }

    where:
    metadata << [
      [scopes: ['foo']],
      [:]
    ]
    app << ['foo', 'spinnaker']
  }
}
