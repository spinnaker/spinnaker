/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit.MockTypedInput
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class PipelineControllerSpec extends Specification {

  def "should update a pipeline"() {
    given:
    def taskSerivce = Mock(TaskService)
    def front50Service = Mock(Front50Service)
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PipelineController(objectMapper: new ObjectMapper(), taskService: taskSerivce, front50Service: front50Service)).build()

    and:
    def pipeline = [
      id: "id",
      name: "test pipeline",
      stages: [],
      triggers: [],
      limitConcurrent: true,
      parallel: true,
      index: 4,
      application: "application"
    ]

    when:
    def response = mockMvc.perform(
      put("/pipelines/${pipeline.id}").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipeline))
    ).andReturn().response

    then:
    response.status == 200
    1 * taskSerivce.createAndWaitForCompletion([
      description: "Update pipeline 'test pipeline'" as String,
      application: 'application',
      job: [
        [
          type: 'updatePipeline',
          pipeline: Base64.encoder.encodeToString(new ObjectMapper().writeValueAsString([
            id: 'id',
            name: 'test pipeline',
            stages: [],
            triggers: [],
            limitConcurrent: true,
            parallel: true,
            index: 4,
            application: 'application'
          ]).bytes),
          user: 'anonymous'
        ]
      ]
    ]) >> { [id: 'task-id', application: 'application', status: 'SUCCEEDED'] }
    1 * front50Service.getPipelineConfigsForApplication('application', false) >> []
  }

  def "should propagate pipeline template errors"() {
    given:
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PipelineController(objectMapper: new ObjectMapper())).build()

    and:
    def pipeline = [
      type: 'templatedPipeline',
      config: [:]
    ]
    def mockedHttpException = [
      errors: [
        [
          location: "configuration:stages.meh",
          message: "Stage configuration is unset"
        ]
      ]
    ]

    when:
    mockMvc.perform(
      post("/start").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipeline))
    ).andDo({
      // thanks groovy
      throw RetrofitError.httpError(
        "http://orca",
        new Response("http://orca", 400, "template invalid", [], new MockTypedInput(new JacksonConverter(), mockedHttpException)),
        new JacksonConverter(),
        Object.class
      )
    })

    then:
    def e = thrown(RetrofitError)
    e.body == mockedHttpException
  }
}
