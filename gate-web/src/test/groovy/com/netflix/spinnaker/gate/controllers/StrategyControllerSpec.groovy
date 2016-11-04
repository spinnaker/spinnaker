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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.StrategyService
import org.codehaus.jackson.map.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class StrategyControllerSpec extends Specification {

  def "should update a strategy"() {
    given:
    def strategyService = Mock(StrategyService)
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StrategyController(strategyService: strategyService)).build()

    and:
    def strategy = [
      id: "id",
      name: "test strategy",
      stages: [],
      triggers: [],
      limitConcurrent: true,
      parallel: true,
      index: 4,
      application: "application"
    ]

    when:
    def response = mockMvc.perform(
      put("/strategies/${strategy.id}").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(strategy))
    ).andReturn().response

    then:
    response.status == 200
    1 * strategyService.update(strategy.id, strategy)
  }
}
