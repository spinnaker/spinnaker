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
import com.netflix.spinnaker.gate.config.controllers.PipelineControllerConfigProperties
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit.MockTypedInput
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class PipelineControllerSpec extends Specification {

  def taskSerivce = Mock(TaskService)
  def front50Service = Mock(Front50Service)
  def pipelineControllerConfig = new PipelineControllerConfigProperties()
  def mockMvc = MockMvcBuilders
    .standaloneSetup(new PipelineController(objectMapper: new ObjectMapper(),
      taskService: taskSerivce,
      front50Service: front50Service,
      pipelineControllerConfigProperties: pipelineControllerConfig))
    .build()

  def "should update a pipeline"() {
    given:
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
    1 * front50Service.getPipelineConfigsForApplication('application', true) >> []
  }

  def "should propagate pipeline template errors"() {
    given:
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

  def "should bulk save pipelines"() {
    given:
    def pipelines = [
      [
        id: "pipeline1_id",
        name: "test pipeline",
        application: "application"
      ],
      [
        id: "pipeline2_id",
        name: "test pipeline",
        application: "application2"
      ]
    ]
    def createAndWaitResult = [
      id: 'task-id',
      application: 'bulk_save_placeholder_app',
      status: 'SUCCEEDED',
      variables: [
        [
          key: "isBulkSavingPipelines",
          value: true
        ],
        [
          key: "bulksave",
          value: [
            successful_pipelines_count: 2,
            failed_pipelines_count: 0,
            failed_pipelines_list: []
          ]
        ]
      ]
    ]

    when:
    def response = mockMvc
      .perform(
        post("/pipelines/bulksave")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelines)))
      .andReturn()
      .response

    then:
    response.status == 200
    1 * taskSerivce.createAndWaitForCompletion(
      [
        description: "Bulk save pipelines",
        application: 'bulk_save_placeholder_app',
        job: [
          [
            type                        : 'savePipeline',
            pipelines                   : Base64.encoder
              .encodeToString(new ObjectMapper().writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8)),
            user                        : 'anonymous',
            isBulkSavingPipelines       : true
          ]
        ]
      ],
      300,
      2000
    ) >> createAndWaitResult
    new JsonSlurper().parseText(response.getContentAsString()) == [
      "successful_pipelines_count": 2,
      "failed_pipelines_count": 0,
      "failed_pipelines_list": []
    ]

    // test with custom app
    when:
    response = mockMvc
      .perform(
        post("/pipelines/bulksave")
          .param("application", "my_test_app")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelines)))
      .andReturn()
      .response

    then:
    response.status == 200
    1 * taskSerivce.createAndWaitForCompletion(
      [
        description: "Bulk save pipelines",
        application: 'my_test_app',
        job: [
          [
            type                        : 'savePipeline',
            pipelines                   : Base64.encoder
              .encodeToString(new ObjectMapper().writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8)),
            user                        : 'anonymous',
            isBulkSavingPipelines       : true
          ]
        ]
      ],
      300,
      2000
    ) >> createAndWaitResult

    // Test with custom task completion configs
    when:
    pipelineControllerConfig.getBulksave().maxPollsForTaskCompletion = 10
    pipelineControllerConfig.getBulksave().taskCompletionCheckIntervalMs = 200
    response = mockMvc
      .perform(
        post("/pipelines/bulksave")
          .param("application", "my_test_app")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelines)))
      .andReturn()
      .response

    then:
    response.status == 200
    1 * taskSerivce.createAndWaitForCompletion(
      [
        description: "Bulk save pipelines",
        application: 'my_test_app',
        job: [
          [
            type                        : 'savePipeline',
            pipelines                   : Base64.encoder
              .encodeToString(new ObjectMapper().writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8)),
            user                        : 'anonymous',
            isBulkSavingPipelines       : true
          ]
        ]
      ],
      10,
      200
    ) >> createAndWaitResult
  }

  def "bulk save raises exception on failure"() {
    given:
    def pipelines = [
      [
        id: "pipeline1_id",
        name: "test pipeline",
        application: "application"
      ]
    ]

    when:
    def response = mockMvc
      .perform(
        post("/pipelines/bulksave")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelines)))
      .andReturn()
      .response

    then:
    1 * taskSerivce.createAndWaitForCompletion(
      [
        description: "Bulk save pipelines",
        application: 'bulk_save_placeholder_app',
        job: [
          [
            type                        : 'savePipeline',
            pipelines                   : Base64.encoder
              .encodeToString(new ObjectMapper().writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8)),
            user                        : 'anonymous',
            isBulkSavingPipelines       : true
          ]
        ]
      ],
      300,
      2000
    ) >> {
      [
        id: 'task-id',
        application: 'bulk_save_placeholder_app',
        status: 'TERMINAL',
        variables:
          [
            [
              key: "exception",
              value: [ details: [ errors: ["error happened"]
              ]
              ]
            ]
          ]
      ]
    }
    response.status == 400
    response.contentAsString == ""
  }
}
