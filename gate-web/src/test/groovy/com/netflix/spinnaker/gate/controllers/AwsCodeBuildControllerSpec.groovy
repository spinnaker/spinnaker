/*
 * Copyright 2020 Amazon.com, Inc.
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
import com.netflix.spinnaker.gate.services.internal.IgorService
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class AwsCodeBuildControllerSpec extends Specification {

  MockMvc mockMvc
  IgorService igorService

  def server = new MockWebServer()

  @Shared def objectMapper = new ObjectMapper()
  @Shared def ACCOUNT = 'myAccount'

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    igorService = Mock(IgorService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(
      new AwsCodeBuildController(igorService)).build()
  }

  void 'should get a list of accounts'() {
    given:
    def accounts = ["account1", "account2"]
    1 * igorService.getAwsCodeBuildAccounts() >> accounts

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/codebuild/accounts")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == objectMapper.writeValueAsString(accounts)
  }

  void 'should get an empty list when no accounts found'() {
    given:
    1 * igorService.getAwsCodeBuildAccounts() >> []

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/codebuild/accounts")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == '[]'
  }
}
