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

import groovy.json.JsonSlurper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class OperationsControllerSpec extends Specification {

  MockMvc mockMvc
  def pipelineStarter = Mock(PipelineStarter)

  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(
      new OperationsController(objectMapper: new OrcaObjectMapper(), pipelineStarter: pipelineStarter)
    ).build()
  }

  @Unroll
  void '/orchestrate accepts #contentType'() {
    when:
    def resp = mockMvc.perform(
      post('/orchestrate').contentType(contentType).content('{}')
    ).andReturn().response

    then:
    1 * pipelineStarter.start(_) >> pipeline

    and:
    resp.status == 200
    new JsonSlurper().parseText(resp.contentAsString).ref == "/pipelines/$pipeline.id"

    where:
    contentType << [MediaType.APPLICATION_JSON, MediaType.valueOf('application/context+json')]
    pipeline = new Pipeline(id: "1")
  }
}
