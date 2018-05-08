/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.*
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  def executionRepository = Mock(ExecutionRepository)
  def front50Service = Mock(Front50Service)
  def executionRunner = Mock(ExecutionRunner)

  def clock = Clock.fixed(Instant.now(), UTC)
  int daysOfExecutionHistory = 14
  int numberOfOldPipelineExecutionsToInclude = 2

  ObjectMapper objectMapper = OrcaObjectMapper.newInstance()

  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(
        front50Service: front50Service,
        executionRepository: executionRepository,
        executionRunner: executionRunner,
        daysOfExecutionHistory: daysOfExecutionHistory,
        numberOfOldPipelineExecutionsToInclude: numberOfOldPipelineExecutionsToInclude,
        clock: clock
      )
    ).build()
  }

  void 'should cancel a list of tasks by id'() {
    when:
    def response = mockMvc.perform(
      put('/tasks/cancel').contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(["id1", "id2"]))
    )

    then:
    response.andExpect(status().isAccepted())
    1 * executionRepository.cancel(ORCHESTRATION, 'id2', _, null)
    1 * executionRepository.cancel(ORCHESTRATION, 'id1', _, null)
  }

  void 'step names are properly translated'() {
    given:
    executionRepository.retrieve(ORCHESTRATION, _) >> orchestration {
      id = "1"
      application = "test"
      stage {
        type = "test"
        tasks = [
          new Task(id: "1", name: 'jobOne', implementingClass: "foo", status: TERMINAL),
          new Task(id: "2", name: 'jobTwo', implementingClass: "foo", status: TERMINAL)
        ]
      }
    }

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    response.status == 200
    with(new JsonSlurper().parseText(response.contentAsString)) {
      steps.name == ['jobOne', 'jobTwo']
    }
  }

  void 'stage contexts are included for orchestrated tasks'() {
    setup:
    def orchestration = orchestration {
      id = "1"
      application = "covfefe"
      stages << new Stage(delegate, "OrchestratedType")
      stages.first().context = [customOutput: "variable"]
    }

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    executionRepository.retrieve(orchestration.type, orchestration.id) >> orchestration

    new JsonSlurper().parseText(response.contentAsString).variables == [
      [key: "customOutput", value: "variable"]
    ]
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get("/applications/$app/tasks")).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrationsForApplication(app, _) >> []

    where:
    app = "test"
  }

  void '/applications/{application}/tasks only returns un-started and tasks from the past two weeks, sorted newest first'() {
    given:
    def tasks = [
      [id: "too-old", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli()],
      [id: "not-too-old", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(1, HOURS).toEpochMilli()],
      [id: "pretty-new", application: "covfefe", startTime: clock.instant().minus(1, DAYS).toEpochMilli()],
      [id: 'not-started-1', application: "covfefe"],
      [id: 'not-started-2', application: "covfefe"]
    ].collect { config ->
      orchestration {
        id = config.id
        application = config.application
        startTime = config.startTime
      }
    }
    def app = 'test'
    executionRepository.retrieveOrchestrationsForApplication(app, _) >> tasks

    when:
    def response = new ObjectMapper().readValue(
      mockMvc.perform(get("/applications/$app/tasks")).andReturn().response.contentAsString, ArrayList)

    then:
    response.id == ['not-started-2', 'not-started-1', 'not-too-old', 'pretty-new']
  }

  void '/applications/{application}/pipelines should only return pipelines from the past two weeks, newest first'() {
    given:
    def app = 'test'
    def pipelines = [
      [pipelineConfigId: "1", id: "old", application: app, startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "1", id: "newer", application: app, startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "1", id: 'not-started', application: app],
      [pipelineConfigId: "1", id: 'also-not-started', application: app],

      /*
       * If a pipeline has no recent executions, the most recent N executions should be included
       */
      [pipelineConfigId: "2", id: 'older1', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older2', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(3, HOURS).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older3', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(4, HOURS).toEpochMilli()]
    ]

    executionRepository.retrievePipelinesForPipelineConfigId("1", _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    }
    executionRepository.retrievePipelinesForPipelineConfigId("2", _) >> pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect { config ->
      pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    }
    front50Service.getPipelines(app, false) >> [[id: "1"], [id: "2"]]
    front50Service.getStrategies(app) >> []

    when:
    def response = mockMvc.perform(get("/applications/$app/pipelines")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['not-started', 'also-not-started', 'older2', 'older1', 'newer']
  }

  void '/pipelines should only return the latest pipelines for the provided config ids, newest first'() {
    given:
    def pipelines = [
      [pipelineConfigId: "1", id: "started-1", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-1'],
      [pipelineConfigId: "1", id: "started-2", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli(), id: 'newer-1'],
      [pipelineConfigId: "1", id: 'not-started-1', application: "covfefe"],
      [pipelineConfigId: "2", id: "started-3", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-2'],
      [pipelineConfigId: "2", id: "started-4", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli(), id: 'newer-2'],
      [pipelineConfigId: "2", id: 'not-started-2', application: "covfefe"],
      [pipelineConfigId: "3", id: "started-5", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-3']
    ]

    executionRepository.retrievePipelinesForPipelineConfigId("1", _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    }
    executionRepository.retrievePipelinesForPipelineConfigId("2", _) >> pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    }
    executionRepository.retrievePipelinesForPipelineConfigId("3", _) >> pipelines.findAll {
      it.pipelineConfigId == "3"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    }

    when:
    def response = mockMvc.perform(get("/pipelines?pipelineConfigIds=1,2")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['newer-2', 'newer-1']
  }

  void 'should update existing stage context'() {
    given:
    def pipeline = Execution.newPipeline("covfefe")
    def pipelineStage = new Stage(pipeline, "test", [value: "1"])
    pipelineStage.id = "s1"
    pipeline.stages.add(pipelineStage)

    when:
    def response = mockMvc.perform(patch("/pipelines/$pipeline.id/stages/s1").content(
      objectMapper.writeValueAsString([judgmentStatus: "stop"])
    ).contentType(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    1 * executionRepository.retrieve(pipeline.type, pipeline.id) >> pipeline
    1 * executionRepository.storeStage({ stage ->
      stage.id == "s1" &&
        stage.lastModified.allowedAccounts.isEmpty() &&
        stage.lastModified.user == "anonymous" &&
        stage.context == [
        judgmentStatus: "stop", value: "1", lastModifiedBy: "anonymous"
      ]
    } as Stage)
    1 * executionRunner.reschedule(pipeline)
    0 * _

    and:
    objectMapper.readValue(response.contentAsString, Map).stages*.context == [
      [value: "1", judgmentStatus: "stop", lastModifiedBy: "anonymous"]
    ]
  }
}
