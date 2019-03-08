/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.S3StorageService
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver

import java.util.concurrent.Executors
import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.utils.S3TestHelper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath


abstract class PipelineControllerTck extends Specification {

  static final int OK = 200
  static final int BAD_REQUEST = 400
  static final int UNPROCESSABLE_ENTITY = 422

  MockMvc mockMvc

  @Subject PipelineDAO pipelineDAO

  void setup() {
    this.pipelineDAO = createPipelineDAO()

    mockMvc = MockMvcBuilders
      .standaloneSetup(new PipelineController(pipelineDAO, new ObjectMapper(), Optional.empty()))
      .setHandlerExceptionResolvers(createExceptionResolver())
      .build()
  }

  private static ExceptionHandlerExceptionResolver createExceptionResolver() {
    def resolver = new SimpleExceptionHandlerExceptionResolver()
    resolver.afterPropertiesSet()
    return resolver
  }

  abstract PipelineDAO createPipelineDAO()

  def "should fail to save if application is missing"() {
    given:
    def command = [
      name: "some pipeline with no application"
    ]

    when:
    def response = mockMvc
      .perform(
      post("/pipelines")
        .contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(command))
    )
      .andReturn()
      .response

    then:
    response.status == UNPROCESSABLE_ENTITY
  }

  void "should provide a valid, unique index when listing all for an application"() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name: "c", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a3", application: "test", index: 3
    ]))

    when:
    def response = mockMvc.perform(get("/pipelines/test"))

    then:
    response
      .andExpect(jsonPath('$.[*].name').value(["a1", "b1", "a3", "b", "c"]))
      .andExpect(jsonPath('$.[*].index').value([0, 1, 2, 3, 4]))
  }

  void 'should update a pipeline'() {
    given:
    def pipeline = pipelineDAO.create(null, new Pipeline([name: "test pipeline", application: "test_application"]))

    when:
    pipeline.name = "Updated Name"
    def response = mockMvc.perform(put("/pipelines/${pipeline.id}").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipeline))).andReturn().response

    then:
    response.status == OK
    pipelineDAO.findById(pipeline.getId()).getName() == "Updated Name"

  }

  void 'should fail update on duplicate pipeline or invalid request'() {
    given:
    def (pipeline1, pipeline2) = [
      pipelineDAO.create(null, new Pipeline([name: "test pipeline 1", application: "test_application"])),
      pipelineDAO.create(null, new Pipeline([name: "test pipeline 2", application: "test_application"]))
    ]

    and:
    pipeline1.name = pipeline2.name

    when:
    def response = mockMvc.perform(put("/pipelines/${pipeline1.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipeline1))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A pipeline with name ${pipeline2.name} already exists in application ${pipeline2.application}"

    when:
    response = mockMvc.perform(put("/pipelines/${pipeline2.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipeline1))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "The provided id ${pipeline2.id} doesn't match the pipeline id ${pipeline1.id}"
  }

  @Unroll
  void 'should only (re)generate cron trigger ids for new pipelines'() {
    given:
    def pipeline = [
        name       : "My Pipeline",
        application: "test",
        triggers   : [
            [type: "cron", id: "original-id"]
        ]
    ]
    if (lookupPipelineId) {
      pipelineDAO.create(null, pipeline as Pipeline)
      pipeline.id = pipelineDAO.findById(
          pipelineDAO.getPipelineId("test", "My Pipeline")
      ).getId()
    }

    when:
    def response = mockMvc.perform(post('/pipelines').
        contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(pipeline)))
        .andReturn().response

    def updatedPipeline = pipelineDAO.findById(
        pipelineDAO.getPipelineId("test", "My Pipeline")
    )

    then:
    response.status == OK
    expectedTriggerCheck.call(updatedPipeline)

    where:
    lookupPipelineId || expectedTriggerCheck
    false            || { Map p -> p.triggers*.id != ["original-id"] }
    true             || { Map p -> p.triggers*.id == ["original-id"] }
  }

  void 'should ensure that all cron triggers have an identifier'() {
    given:
    def pipeline = [
      name       : "My Pipeline",
      application: "test",
      triggers   : [
        [type: "cron", id: "original-id", expression: "1"],
        [type: "cron", expression: "2"],
        [type: "cron", id: "", expression: "3"]
      ]
    ]

    pipelineDAO.create(null, pipeline as Pipeline)
    pipeline.id = pipelineDAO.findById(
      pipelineDAO.getPipelineId("test", "My Pipeline")
    ).getId()

    when:
    def response = mockMvc.perform(post('/pipelines').
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(pipeline)))
      .andReturn().response

    def updatedPipeline = pipelineDAO.findById(
      pipelineDAO.getPipelineId("test", "My Pipeline")
    )

    then:
    response.status == OK
    updatedPipeline.get("triggers").find { it.expression == "1" }.id == "original-id"
    updatedPipeline.get("triggers").find { it.expression == "2" }.id.length() > 1
    updatedPipeline.get("triggers").find { it.expression == "3" }.id.length() > 1
  }

  void 'should delete an existing pipeline by name or id'() {
    given:
    pipelineDAO.create(null, new Pipeline([
        name: "pipeline1", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
        name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineDAO.all()
    def allPipelinesForApplication = pipelineDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(delete('/pipelines/test/pipeline1')).andReturn().response

    then:
    response.status == OK
    pipelineDAO.all()*.name == ["pipeline2"]
  }

  void 'should enforce unique names on save operations'() {
    given:
    pipelineDAO.create(null, new Pipeline([
            name: "pipeline1", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
            name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineDAO.all()
    def allPipelinesForApplication = pipelineDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(post('/pipelines')
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString([name: "pipeline1", application: "test"])))
                  .andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A pipeline with name pipeline1 already exists in application test"
  }
}

@IgnoreIf({ S3TestHelper.s3ProxyUnavailable() })
class S3PipelineControllerTck extends PipelineControllerTck {
  @Shared
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @Shared
  PipelineDAO pipelineDAO

  @Override
  PipelineDAO createPipelineDAO() {
    def amazonS3 = new AmazonS3Client(new ClientConfiguration())
    amazonS3.setEndpoint("http://127.0.0.1:9999")
    S3TestHelper.setupBucket(amazonS3, "front50")

    def storageService = new S3StorageService(new ObjectMapper(), amazonS3, "front50", "test", false, "us-east-1", true, 10_000)
    pipelineDAO = new DefaultPipelineDAO(storageService, scheduler,new DefaultObjectKeyLoader(storageService), 0,false, new NoopRegistry())

    return pipelineDAO
  }
}
