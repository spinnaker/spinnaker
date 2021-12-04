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
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.ServiceAccountsService
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import org.springframework.beans.factory.ObjectProvider

import java.time.Clock
import java.util.concurrent.Executors
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper

import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
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

  @Subject
  PipelineDAO pipelineDAO
  def serviceAccountsService

  void setup() {
    this.pipelineDAO = createPipelineDAO()
    this.serviceAccountsService = Mock(ServiceAccountsService)

    mockMvc = MockMvcBuilders
      .standaloneSetup(
        new PipelineController(
          pipelineDAO,
          new ObjectMapper(),
          Optional.of(serviceAccountsService),
          Collections.emptyList(),
          Optional.empty()
        )
      )
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()
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
    response.errorMessage == "The provided id ${pipeline1.id} doesn't match the pipeline id ${pipeline2.id}"
  }

  @Unroll
  void 'should only (re)generate cron trigger ids for new pipelines'() {
    given:
    def pipeline = [
      name       : "My Pipeline",
      application: "test",
      triggers   : [
        new Trigger([type: "cron", id: "original-id"])
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
    false            || { Pipeline p -> p.triggers*.id != ["original-id"] }
    true             || { Pipeline p -> p.triggers*.id == ["original-id"] }
  }

  void 'should ensure that all cron triggers have an identifier'() {
    given:
    def pipeline = [
      name       : "My Pipeline",
      application: "test",
      triggers   : [
        new Trigger([type: "cron", id: "original-id", expression: "1"]),
        new Trigger([type: "cron", expression: "2"]),
        new Trigger([type: "cron", id: "", expression: "3"])
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
    updatedPipeline.getTriggers().find { it.expression == "1" }.id == "original-id"
    updatedPipeline.getTriggers().find { it.expression == "2" }.id.length() > 1
    updatedPipeline.getTriggers().find { it.expression == "3" }.id.length() > 1
  }

  void 'should delete an existing pipeline by name or id and its associated managed service account'() {
    given:
    def pipelineToDelete = pipelineDAO.create(null, new Pipeline([
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
    1 * serviceAccountsService.deleteManagedServiceAccounts([pipelineToDelete.id])
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

  @Unroll
  void "pipeline with limitConcurrent = #limitConcurrent and maxConcurrentExecutions = #maxConcurrentExecutions"() {
    def appName = "test"
    def pipelineName = "a_pipeline"
    def Map pipelineData
    given:
    if (type == "max") {
      pipelineData = [
        name: pipelineName,
        application: appName,
        limitConcurrent: limitConcurrent,
        maxConcurrentExecutions: maxConcurrentExecutions
      ]
    } else {
      pipelineData = [
        name: pipelineName,
        application: appName,
        limitConcurrent: limitConcurrent
      ]
    }

    when:
    def postResponse = mockMvc.perform(
      post("/pipelines")
        .contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response
    def postContent = new JsonSlurper().parseText(postResponse.contentAsString)
    def id_from_db = pipelineDAO.getPipelineId(appName, pipelineName)
    def getResponse = mockMvc.perform(
      get("/pipelines/"+id_from_db+"/get"))
      .andReturn()
      .response
    def getContent = new JsonSlurper().parseText(getResponse.contentAsString)

    then:
    postResponse.status == OK
    postContent.id == id_from_db

    getResponse.status == OK
    getContent.limitConcurrent == expectedLimitConcurrent
    getContent.maxConcurrentExecutions == expectedMaxConcurrentExecutions

    where:
      type | limitConcurrent | maxConcurrentExecutions || expectedLimitConcurrent || expectedMaxConcurrentExecutions
      "limit" | false | "not_set" || false || null
      "limit" | true | "not_set" || true || null
      "max" | true | 0 || true || 0
      "max" | true | 5 || true || 5
  }
}

class SqlPipelineControllerTck extends PipelineControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  PipelineDAO createPipelineDAO() {
    def registry = new NoopRegistry()

    def storageService = new SqlStorageService(
      new ObjectMapper(),
      registry,
      currentDatabase.context,
      Clock.systemDefaultZone(),
      new SqlRetryProperties(),
      100,
      "default"
    )

    return new DefaultPipelineDAO(
      storageService,
      scheduler,
      new DefaultObjectKeyLoader(storageService),
      0,
      false,
      new NoopRegistry(),
      new InMemoryCircuitBreakerRegistry()
    )
  }
}
