/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.spinnaker.gate.services.BuildService
import com.netflix.spinnaker.gate.services.internal.IgorService
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class BuildControllerSpec extends Specification {

  MockMvc mockMvc
  BuildService buildService
  IgorService igorService

  def server = new MockWebServer()

  final MASTER = 'MASTER'
  final BUILD_NUMBER = 123
  final JOB_NAME = "name/with/slashes and spaces"
  final JOB_NAME_LEGACY = "job"
  final JOB_NAME_ENCODED = "name/with/slashes%20and%20spaces"

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    igorService = Mock(IgorService)
    buildService = new BuildService(igorService: igorService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new BuildController(buildService: buildService)).build()
  }

  void 'should get a list of masters'() {
    given:
    1 * igorService.getBuildMasters() >> [MASTER, "master2"]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/v2/builds")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[\"${MASTER}\",\"master2\"]"
  }

  void 'should get a list of jobs for a master'() {
    given:
    1 * igorService.getJobsForBuildMaster(MASTER) >> [JOB_NAME, "another_job"]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/v2/builds/${MASTER}/jobs")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[\"${JOB_NAME}\",\"another_job\"]"
  }

  void 'should get a list of builds for a job'() {
    given:
    1 * igorService.getBuilds(MASTER, JOB_NAME_ENCODED) >> [["building": false, "number": 111], ["building": false, "number": 222]]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/v2/builds/${MASTER}/builds/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[{\"building\":false,\"number\":111},{\"building\":false,\"number\":222}]"
  }

  void 'should get a job config'() {
    given:
    1 * igorService.getJobConfig(MASTER, JOB_NAME_ENCODED) >> ['name': JOB_NAME, 'url': "http://test.com/job/${JOB_NAME}".toString()]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/v2/builds/${MASTER}/jobs/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"name\":\"${JOB_NAME}\",\"url\":\"http://test.com/job/${JOB_NAME}\"}"
  }

  void 'should get a build'() {
    given:
    1 * igorService.getBuild(MASTER, JOB_NAME_ENCODED, BUILD_NUMBER.toString()) >> ["building": false, "number": BUILD_NUMBER]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/v2/builds/${MASTER}/build/${BUILD_NUMBER}/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
  }

}
