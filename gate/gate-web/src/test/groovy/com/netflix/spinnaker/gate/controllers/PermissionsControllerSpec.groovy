/*
 * Copyright 2026 DoorDash, Inc.
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

import com.netflix.spinnaker.gate.services.internal.Front50Service
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit2.mock.Calls
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class PermissionsControllerSpec extends Specification {

  MockMvc mockMvc
  Front50Service front50Service = Mock(Front50Service)

  void setup() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(new PermissionsController(front50Service: front50Service))
      .build()
  }

  def "serves the global default application permissions from front50"() {
    given:
    front50Service.getDefaultApplicationPermissions() >> Calls.response([READ: ["everyone"], WRITE: ["admins"]])

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/permissions/defaults")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == '{"READ":["everyone"],"WRITE":["admins"]}'
  }

  def "an install with no defaults configured gets an empty object, not an error"() {
    given:
    front50Service.getDefaultApplicationPermissions() >> Calls.response([:])

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/permissions/defaults")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == '{}'
  }
}
