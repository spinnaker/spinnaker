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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  ExecutionRepository executionRepository

  void setup() {
    executionRepository = Mock(ExecutionRepository)
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(executionRepository: executionRepository)
    ).build()
  }

  void '/tasks returns a list of active tasks'() {
    when:
    mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrations()
  }

  void '/tasks are sorted by startTime, with non-started tasks first'() {
    given:
    def tasks = [
      [ startTime: 1, id: 'c' ],
      [ startTime: 2, id: 'd' ],
      [ id: 'b' ],
      [ id: 'a' ]
    ]

    when:
    def response = mockMvc.perform(get("/pipelines")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    1 * executionRepository.retrievePipelines() >> tasks
    results.id == [ 'b', 'a', 'd', 'c']
  }

  void 'step names are properly translated'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    executionRepository.retrieveOrchestrations() >> [new Orchestration(stages: [new OrchestrationStage(tasks: [new DefaultTask(name: 'jobOne'), new DefaultTask(name: 'jobTwo')])])]
    with(new ObjectMapper().readValue(response.contentAsString, new TypeReference<List<OrchestrationViewModel>>() {}).first()) {
      steps.name == ['jobOne', 'jobTwo']
    }

  }

  void 'stage contexts are included for orchestrated tasks'() {
    setup:
    def orchestration = new Orchestration(id: "1")
    orchestration.stages = [
      new OrchestrationStage(orchestration, "OrchestratedType", ["customOutput": "variable"])
    ]

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    executionRepository.retrieveOrchestration(orchestration.id) >> orchestration

    new ObjectMapper().readValue(response.contentAsString, OrchestrationViewModel).variables == [
      [key: "customOutput", value: "variable"]
    ]
  }

  void '/tasks returns [] when there are no tasks'() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrations() >> []
    response.status == 200
    response.contentAsString == '[]'
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get("/applications/$app/tasks")).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrationsForApplication(app)

    where:
    app = "test"
  }

  void '/pipelines should return a list of pipelines'() {
    when:
    def response = mockMvc.perform(get("/pipelines")).andReturn().response

    then:
    1 * executionRepository.retrievePipelines() >> [new Pipeline(), new Pipeline()]
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.size() == 2
  }

  void '/pipelines sorted by startTime, with non-started pipelines first'() {
    given:
    def pipelines = [
      [ startTime: 1, id: 'c' ],
      [ startTime: 2, id: 'd' ],
      [ id: 'b' ],
      [ id: 'a' ]
    ]

    when:
    def response = mockMvc.perform(get("/pipelines")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    1 * executionRepository.retrievePipelines() >> pipelines
    results.id == [ 'b', 'a', 'd', 'c']

  }
}
