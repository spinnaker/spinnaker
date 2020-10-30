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


package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineStrategyDAO
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver
import rx.schedulers.Schedulers
import spock.lang.*

import java.time.Clock
import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

abstract class StrategyControllerTck extends Specification {

  static final int OK = 200
  static final int BAD_REQUEST = 400

  MockMvc mockMvc

  @Subject
  PipelineStrategyDAO pipelineStrategyDAO

  void setup() {
    this.pipelineStrategyDAO = createPipelineStrategyDAO()

    mockMvc = MockMvcBuilders
      .standaloneSetup(new StrategyController(pipelineStrategyDAO))
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()
  }

  abstract PipelineStrategyDAO createPipelineStrategyDAO()

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
      pipelineStrategyDAO.create(null, pipeline as Pipeline)
      pipeline.id = pipelineStrategyDAO.findById(
        pipelineStrategyDAO.getPipelineId("test", "My Pipeline")
      ).getId()
    }

    when:
    def response = mockMvc.perform(post('/strategies').
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(pipeline)))
      .andReturn().response

    def updatedPipeline = pipelineStrategyDAO.findById(
      pipelineStrategyDAO.getPipelineId("test", "My Pipeline")
    )

    then:
    response.status == OK
    expectedTriggerCheck.call(updatedPipeline)

    where:
    lookupPipelineId || expectedTriggerCheck
    false            || { Map p -> p.triggers*.id != ["original-id"] }
    true             || { Map p -> p.triggers*.id == ["original-id"] }
  }

  void 'should update a strategy'() {
    given:
    def strategy = pipelineStrategyDAO.create(null, new Pipeline([name: "test pipeline", application: "test_application"]))

    when:
    strategy.name = "Updated Name"
    def response = mockMvc.perform(put("/strategies/${strategy.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(strategy))).andReturn().response

    then:
    response.status == OK
    pipelineStrategyDAO.findById(strategy.getId()).getName() == "Updated Name"

  }

  void 'should fail update on duplicate strategy or invalid request'() {
    given:
    def (strategy1, strategy2) = [
      pipelineStrategyDAO.create(null, new Pipeline([name: "test strategy 1", application: "test_application"])),
      pipelineStrategyDAO.create(null, new Pipeline([name: "test strategy 2", application: "test_application"]))
    ]

    and:
    strategy1.name = strategy2.name

    when:
    def response = mockMvc.perform(put("/strategies/${strategy1.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(strategy1))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A strategy with name '${strategy2.name}' already exists in application '${strategy2.application}'"

    when:
    response = mockMvc.perform(put("/strategies/${strategy1.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(strategy2))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "The provided id '${strategy1.id}' doesn't match the strategy id '${strategy2.id}'"
  }

  void 'should delete an existing pipeline by name or id'() {
    given:
    pipelineStrategyDAO.create(null, new Pipeline([
      name: "pipeline1", application: "test"
    ]))
    pipelineStrategyDAO.create(null, new Pipeline([
      name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineStrategyDAO.all()
    def allPipelinesForApplication = pipelineStrategyDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(delete('/strategies/test/pipeline1')).andReturn().response

    then:
    response.status == OK
    pipelineStrategyDAO.all()*.name == ["pipeline2"]
  }

  void 'should enforce unique names on save operations'() {
    given:
    pipelineStrategyDAO.create(null, new Pipeline([
      name: "pipeline1", application: "test"
    ]))
    pipelineStrategyDAO.create(null, new Pipeline([
      name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineStrategyDAO.all()
    def allPipelinesForApplication = pipelineStrategyDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(post('/strategies')
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString([name: "pipeline1", application: "test"])))
      .andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A strategy with name pipeline1 already exists in application test"
  }
}

class SqlStrategyControllerTck extends StrategyControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  PipelineStrategyDAO createPipelineStrategyDAO() {
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

    return new DefaultPipelineStrategyDAO(
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
