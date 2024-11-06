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
import com.netflix.spinnaker.gate.config.ApplicationConfigurationProperties
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.util.NestedServletException
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
    mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationController(applicationService: applicationService)).build()
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
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> configs
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
    1 * front50Service.getPipelineConfigsForApplication('true-app', 'pipelineA', true) >> configs
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
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> configs
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs[0]))

    where:
    endpoint << ["/applications/true-app/pipelineConfigs/some-true-pipeline"]
  }

  @Unroll
  void 'should return 404 on pipeline that does not exists' (){
    given:
    def configs = [
      [
        name: 'some-true-pipeline',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    when:
    mockMvc.perform(get(endpoint))

    then:
    1 * front50Service.getPipelineConfigsForApplication('true-app', null, true) >> configs
    NestedServletException ex = thrown()
    ex.message.contains('Pipeline config (id: some-fake-pipeline) not found for Application (id: true-app)')

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
    1 * front50Service.getStrategyConfigs('true-app') >> configs
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(configs[0]))

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-true-strategy"]
  }

  @Unroll
  void 'should return 404 with strategy configuration for strategy not exists' (){
    given:
    def configs = [
      [
        name: 'some-true-strategy',
        some: 'some-random-x',
        someY: 'some-random-y'
      ]
    ]
    when:
    mockMvc.perform(get(endpoint))

    then:
    1 * front50Service.getStrategyConfigs('true-app') >> configs
    NestedServletException ex = thrown()
    ex.message.contains('Strategy config (id: some-fake-strategy) not found for Application (id: true-app)')

    where:
    endpoint << ["/applications/true-app/strategyConfigs/some-fake-strategy"]
  }

}
