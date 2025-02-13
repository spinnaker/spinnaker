/*
 * Copyright 2019 Andres Castano
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
import com.netflix.spinnaker.gate.services.BuildService
import com.netflix.spinnaker.gate.services.internal.GoogleCloudBuildTrigger
import com.netflix.spinnaker.gate.services.internal.IgorService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.mockwebserver.MockWebServer
import okhttp3.ResponseBody;
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class GoogleCloudBuildControllerSpec extends Specification {

  MockMvc mockMvc
  BuildService buildService
  IgorService igorService

  def server = new MockWebServer()

  @Shared def objectMapper = new ObjectMapper()
  @Shared def ACCOUNT = 'myAccount'

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    igorService = Mock(IgorService)
    buildService = new BuildService(igorService: igorService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(
      new GoogleCloudBuildController(igorService, buildService)).build()
  }

  void 'should get a list of triggers for a given account'() {
    def triggers = [
      new GoogleCloudBuildTrigger("trigger1", "myTrigger1", "My desc 1"),
      new GoogleCloudBuildTrigger("trigger2", "myTrigger2", "My desc 2")
      ]
    given:
    1 * igorService.getGoogleCloudBuildTriggers(ACCOUNT) >> Calls.response(triggers)

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/gcb/triggers/${ACCOUNT}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == objectMapper.writeValueAsString(triggers)
  }

  void 'should get an empty list when no triggers defined for a given account'() {
    given:
    1 * igorService.getGoogleCloudBuildTriggers(ACCOUNT) >> Calls.response([])

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/gcb/triggers/${ACCOUNT}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == '[]'
  }

  void 'Should return correct message when account not found in downstream service'() {
    given:
    1 * igorService.getGoogleCloudBuildTriggers(ACCOUNT) >> {
      throw makeSpinnakerHttpException(404)
    }

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/gcb/triggers/${ACCOUNT}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 404
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    Response retrofit2Response =
      Response.error(
        status,
        ResponseBody.create(
          okhttp3.MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

}
