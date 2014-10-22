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
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.batch.core.JobInstance
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Ignore
import spock.lang.Specification


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@Ignore
class OperationsControllerSpec extends Specification {
  MockMvc mockMvc
  PipelineStarter pipelineStarter
  List jobs

  void setup() {
    pipelineStarter = Mock(PipelineStarter)
    mockMvc = MockMvcBuilders.standaloneSetup(
      new OperationsController(objectMapper: new ObjectMapper(), pipelineStarter: pipelineStarter)
    ).build()
    jobs = [
      [instance: new JobInstance(0, 'jobOne'), name: 'jobOne', id: 0],
      [instance: new JobInstance(1, 'jobTwo'), name: 'jobTwo', id: 1]
    ]
  }

  void '/ops accepts application/context+json'() {
    setup:
    def pipeline = Mock(Pipeline)
    pipeline.getId() >> "1"

    when:
    MockHttpServletResponse response = mockMvc.perform(
      post('/ops')
        .contentType(MediaType.valueOf('application/context+json'))
        .content('{}')
    ).andReturn().response

    then:
    pipelineStarter.start(_) >> rx.Observable.from(pipeline)
    response.status == 200
  }

  void '/ops accepts application/json'() {
    setup:
    def jobExecution = Mock(Pipeline)

    when:
    MockHttpServletResponse resp = mockMvc.perform(
      post('/ops')
        .contentType(MediaType.APPLICATION_JSON)
        .content('[]')
    ).andReturn().response

    then:
    pipelineStarter.start(_) >> rx.Observable.from(jobExecution)
    jobExecution.id >> 1
    resp.status == 200
  }
}
