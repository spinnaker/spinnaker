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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.ErrorConfiguration
import com.netflix.spinnaker.gate.config.ApplicationConfigurationProperties
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import okhttp3.mockwebserver.MockWebServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes=[ErrorConfiguration])
class ApplicationControllerSpec extends Specification {

  MockMvc mockMvc
  ApplicationService applicationService
  Front50Service front50Service

  @Autowired
  GenericExceptionHandlers genericExceptionHandlers

  def server = new MockWebServer()
  void cleanup(){
    server.shutdown()
  }

  void setup(){
    front50Service = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def clouddriverSelector = Mock(ClouddriverServiceSelector) {
      select() >> clouddriver
    }

    applicationService = new ApplicationService(
      new ServiceConfiguration(),
      clouddriverSelector,
      front50Service,
      new ApplicationConfigurationProperties()
    )
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationController(applicationService: applicationService)).setControllerAdvice(genericExceptionHandlers)
      .build()
  }

  @Unroll
  void 'should return configs for an application' (){
    given: "random configs"
    def configs = [
      [
        name: 'pipelineA',
        some: 'some-random-x',
      ],
      [
        name: 'pipelineB',
        some: 'some-random-F',
      ],
    ]

    when: "all configs are requested"
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then: "we only call front50 once, and do not pass through the pipelineNameFilter"
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> Calls.response(configs)
    0 * front50Service._

    and: "we get all configs"
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs))

    where:
    endpoint << ["/applications/true-app/pipelineConfigs"]
  }

  @Unroll
  void 'should return configs for an application with pipelineNameFilter' (){
    given: "only one config"
    def configs = [
      [
        name: 'pipelineA',
        some: 'some-random-x',
      ],
    ]

    when: "configs are requested with a filter"
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then: "we only call front50 once, and we do pass through the pipelineNameFilter"
    1 * front50Service.getPipelineConfigsForApplication('true-app', 'pipelineA', true) >> Calls.response(configs)
    0 * front50Service._

    and: "only filtered configs are returned"
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs))

    where:
    endpoint << ["/applications/true-app/pipelineConfigs?pipelineNameFilter=pipelineA"]
  }


  @Unroll
  void 'should return 200 with info on pipeline that exists with config' (){
    given:
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
    when:
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then:
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> Calls.response(configs)
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs[0]))

    where:
    endpoint << ["/applications/true-app/pipelineConfigs/some-true-pipeline"]
  }

  @Unroll
  void 'should return 404 for a pipeline that does not exist' (){
    given:
    def configs = [
      [
        name: 'some-true-pipeline',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    when:
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then:
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> Calls.response(configs)

    and:
    response.andExpect status().isNotFound()
    response.andExpect status().reason("Pipeline config (id: some-fake-pipeline) not found for Application (id: true-app)")

    where:
    endpoint << ["/applications/true-app/pipelineConfigs/some-fake-pipeline"]
  }

  @Unroll
  void 'should return 200 with strategy configuration for strategy exists' (){
    given:
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
    when:
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then:
    1 * front50Service.getStrategyConfigs('true-app') >> Calls.response(configs)
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs[0]))

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-true-strategy"]
  }

  @Unroll
  void 'should return 404 with strategy configuration for a strategy that does not exist' (){
    given:
    def configs = [
      [
        name: 'some-true-strategy',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    when:
    def response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON))

    then:
    1 * front50Service.getStrategyConfigs('true-app') >> Calls.response(configs)

    and:
    response.andExpect status().isNotFound()
    response.andExpect status().reason('Strategy config (id: some-fake-strategy) not found for Application (id: true-app)')

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-fake-strategy"]
  }

}
