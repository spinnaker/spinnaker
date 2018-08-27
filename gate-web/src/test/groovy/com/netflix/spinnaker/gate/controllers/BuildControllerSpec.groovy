/*
 * Copyright 2015 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class BuildControllerSpec extends Specification {

  MockMvc mockMvc
  BuildService buildService
  IgorService igorService

  def server = new MockWebServer()

  @Shared def MASTER = 'MASTER'
  @Shared def BUILD_NUMBER = 123
  @Shared def JOB_NAME = "name/with/slashes and spaces"
  @Shared def JOB_NAME_LEGACY = "job"
  @Shared def JOB_NAME_ENCODED = "name/with/slashes%20and%20spaces"

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    igorService = Mock(IgorService)
    buildService = new BuildService(igorService: igorService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new BuildController(buildService: buildService)).build()
  }

  @Unroll
  void 'should get a list of masters'() {
    given:
    1 * igorService.getBuildMasters() >> [MASTER, "master2"]

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[\"${MASTER}\",\"master2\"]"

    where:
    endpoint << ["/v2/builds", "/v3/builds"]
  }

  @Unroll
  void 'should get a list of jenkins masters'() {
    def masterType = 'jenkins'
    def jenkinsMasters = ['jenkinsX', 'jenkinsY']

    given:
    1 * igorService.getBuildMasters(masterType) >> jenkinsMasters
    0 * igorService.getBuildMasters('wercker') >> _
    0 * igorService.getBuildMasters() >> _

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint).param('type', masterType)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    new JsonSlurper().parseText(response.contentAsString) == jenkinsMasters

    where:
    endpoint << ["/v2/builds", "/v3/builds"]
  }

  @Unroll
  void 'should get a list of wercker masters'() {
    def masterType = 'wercker'
    def werckerMasters = ['wercker-prod', 'wercker-staging']

    given:
    1 * igorService.getBuildMasters(masterType) >> werckerMasters
    0 * igorService.getBuildMasters('jenkins') >> _
    0 * igorService.getBuildMasters() >> _

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint).param('type', masterType)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    new JsonSlurper().parseText(response.contentAsString) == werckerMasters

    where:
    endpoint << ["/v2/builds", "/v3/builds"]
  }

  @Unroll
  void 'should get a list of jobs for a master'() {
    given:
    1 * igorService.getJobsForBuildMaster(MASTER) >> [JOB_NAME, "another_job"]

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[\"${JOB_NAME}\",\"another_job\"]"

    where:
    endpoint << ["/v2/builds/${MASTER}/jobs", "/v3/builds/${MASTER}/jobs"]
  }

  @Unroll
  void 'should get a list of builds for a job'() {
    given:
    1 * igorService.getBuilds(MASTER, JOB_NAME_ENCODED) >> [["building": false, "number": 111], ["building": false, "number": 222]]

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .param("job", JOB_NAME)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[{\"building\":false,\"number\":111},{\"building\":false,\"number\":222}]"

    where:
    endpoint << ["/v2/builds/${MASTER}/builds/${JOB_NAME}", "/v3/builds/${MASTER}/builds"]
  }

  @Unroll
  void 'should get a job config'() {
    given:
    1 * igorService.getJobConfig(MASTER, JOB_NAME_ENCODED) >> ['name': JOB_NAME, 'url': "http://test.com/job/${JOB_NAME}".toString()]

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .param("job", JOB_NAME)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"name\":\"${JOB_NAME}\",\"url\":\"http://test.com/job/${JOB_NAME}\"}"

    where:
    endpoint << ["/v2/builds/${MASTER}/jobs/${JOB_NAME}", "/v3/builds/${MASTER}/job"]
  }

  @Unroll
  void 'should get a build'() {
    given:
    1 * igorService.getBuild(MASTER, JOB_NAME_ENCODED, BUILD_NUMBER.toString()) >> ["building": false, "number": BUILD_NUMBER]

    when:
    MockHttpServletResponse response = mockMvc.perform(get(endpoint)
      .param("job", JOB_NAME)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"

    where:
    endpoint << [
      "/v2/builds/${MASTER}/build/${BUILD_NUMBER}/${JOB_NAME}",
      "/v3/builds/${MASTER}/build/${BUILD_NUMBER}"
    ]
  }

}
