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
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  JobExplorer jobExplorer
  List jobs

  void setup() {
    jobExplorer = Mock(JobExplorer)
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(jobExplorer: jobExplorer)
    ).build()
    jobs = [
      [instance: new JobInstance(0, 'jobOne'), name: 'jobOne', id: 0],
      [instance: new JobInstance(1, 'jobTwo'), name: 'jobTwo', id: 1]
    ]
  }

  void '/tasks returns a list of active tasks'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[0].name, jobs[1].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args -> [new JobExecution(args[0], null)] }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.every { task -> task.name == jobs[tasks.indexOf(task)].name }
    tasks.size() == 2
  }

  void '/tasks returns tasks sorted by id'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[1].name, jobs[0].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args -> [new JobExecution(args[0], args[0].id, null, null)] }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.id == [0, 1]
  }

  void '/tasks returns [] when there are no tasks'() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> []
    response.status == 200
    response.contentAsString == '[]'
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get('/applications/test/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[0].name, jobs[1].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args ->
      def execution = new JobExecution(args[0], null)
      def pipeline = new Pipeline()
      pipeline.application = "test"
      if (args[0].id == 1) {
        execution.getExecutionContext().put('pipeline', pipeline)
      }
      [execution]
    }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.size() == 1
  }
}
