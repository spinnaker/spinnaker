/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.squareup.okhttp.mockwebserver.MockWebServer
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.util.NestedServletException
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class ApplicationControllerSpec extends Specification {

  MockMvc mockMvc
  ApplicationService applicationService
  Front50Service front50Service

  def server = new MockWebServer()
  void cleanup(){
    server.shutdown()
  }

  void setup(){
    front50Service = Mock(Front50Service)
    applicationService = new ApplicationService(front50Service: front50Service)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationController(applicationService: applicationService)).build()
  }


  @Unroll
  void 'should return 200 with info on pipeline that exists with config' (){
    def configs = [
      [
        name: 'some-true-pipeline',
        some: 'some-random-x',
        someY: 'some-random-y'
      ],
      [
        name: 'some-fake-pipeline',
        some: 'some-random-F',
        someY: 'some-random-Z'
      ],
    ]
    given:
      1 * front50Service.getPipelineConfigsForApplication('true-app', true) >> configs
    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    new JsonSlurper().parseText(response.contentAsString) == configs[0]
    response.status == 200

    where:
    endpoint << ["/applications/true-app/pipelineConfigs/some-true-pipeline"]
  }

  @Unroll
  void 'should return 404 on pipeline that does not exists' (){
    def configs = [
      [
        name: 'some-true-pipeline',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    given:
    1 * front50Service.getPipelineConfigsForApplication('true-app', true) >> configs
    when:
    mockMvc.perform(get(endpoint))

    then:
    NestedServletException ex = thrown()
    ex.message.contains('Pipeline config (id: some-fake-pipeline) not found for Application (id: true-app)')

    where:
    endpoint << ["/applications/true-app/pipelineConfigs/some-fake-pipeline"]
  }

  @Unroll
  void 'should return 200 with strategy configuration for strategy exists' (){
    def configs = [
      [
        name: 'some-true-strategy',
        some: 'some-random-x',
        someY: 'some-random-y'
      ],
      [
        name: 'some-fake-strategy',
        some: 'some-random-F',
        someY: 'some-random-Z'
      ],
    ]
    given:
    1 * front50Service.getStrategyConfigs('true-app') >> configs
    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    new JsonSlurper().parseText(response.contentAsString) == configs[0]
    response.status == 200

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-true-strategy"]
  }

  @Unroll
  void 'should return 404 with strategy configuration for strategy not exists' (){
    def configs = [
      [
        name: 'some-true-strategy',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    given:
    1 * front50Service.getStrategyConfigs('true-app') >> configs
    when:
    mockMvc.perform(get(endpoint))

    then:
    NestedServletException ex = thrown()
    ex.message.contains('Strategy config (id: some-fake-strategy) not found for Application (id: true-app)')

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-fake-strategy"]
  }

}
