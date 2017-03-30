/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.build

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.ParameterDefinition
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.service.BuildService
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit.client.Header
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

import java.util.concurrent.Executors

/**
 * Tests for BuildController
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildControllerSpec extends Specification {

    MockMvc mockMvc
    BuildMasters buildMasters
    BuildCache cache
    JenkinsService jenkinsService
    BuildService service
    TravisService travisService

    @Shared
    MockWebServer server

    final MASTER = 'MASTER'
    final HTTP_201 = 201
    final BUILD_NUMBER = 123
    final QUEUED_JOB_NUMBER = 123456
    final JOB_NAME = "job/name/can/have/slashes"
    final JOB_NAME_LEGACY = "job"
    final FILE_NAME = "test.yml"

    void cleanup() {
        server.shutdown()
    }

    void setup() {
        jenkinsService = Mock(JenkinsService)
        service = Mock(BuildService)
        travisService = Mock(TravisService)
        cache = Mock(BuildCache)
        buildMasters = Mock(BuildMasters)
        server = new MockWebServer()
        mockMvc = MockMvcBuilders.standaloneSetup(new BuildController(
            executor: Executors.newSingleThreadExecutor(), buildMasters: buildMasters, objectMapper: new ObjectMapper())).build()
    }

    void 'get the status of a build'() {
        given:
        1 * service.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> new GenericBuild(building: false, number: BUILD_NUMBER)

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/status/${BUILD_NUMBER}/${MASTER}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        3 * buildMasters.map >> [MASTER: service]
        response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
    }

    void 'get an item from the queue'() {
        given:
        1 * jenkinsService.getQueuedItem(QUEUED_JOB_NUMBER) >> new QueuedJob(number: QUEUED_JOB_NUMBER)

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/queue/${MASTER}/${QUEUED_JOB_NUMBER}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == "{\"number\":${QUEUED_JOB_NUMBER}}"
    }

    void 'get a list of builds for a job'() {
        given:
        1 * jenkinsService.getBuilds(JOB_NAME) >> new BuildsList(list: [new Build(number: 111), new Build(number: 222)])

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/all/${MASTER}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == "[{\"building\":false,\"number\":111},{\"building\":false,\"number\":222}]"
    }

    void 'get properties of a build with a bad filename'() {
        given:
        1 * jenkinsService.getBuild(JOB_NAME, BUILD_NUMBER) >> new Build(
             number: BUILD_NUMBER, artifacts: [new BuildArtifact(fileName: FILE_NAME, relativePath: FILE_NAME)])

        when:
        MockHttpServletResponse response = mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${MASTER}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == "{}"
    }

    void 'get properties of a travis build'() {
        given:
        1 * travisService.getBuildProperties(JOB_NAME, BUILD_NUMBER) >> ['foo': 'bar']

        when:
        MockHttpServletResponse response = mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${MASTER}/${JOB_NAME}")
                .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> new HashMap<String, BuildService>()
        1 * buildMasters.filteredMap(BuildServiceProvider.TRAVIS) >> [MASTER: travisService]
        1 * buildMasters.map >> [MASTER: travisService]
        response.contentAsString == "{\"foo\":\"bar\"}"
    }

    void 'trigger a build without parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig()
        1 * jenkinsService.build(JOB_NAME) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${BUILD_NUMBER}")], null)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${MASTER}/jobs/${JOB_NAME}")
          .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == BUILD_NUMBER.toString()

    }

    void 'trigger a build with parameters to a job with parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(parameterDefinitionList: [new ParameterDefinition(defaultName: "name", defaultValue: null, description: "description")])
        1 * jenkinsService.buildWithParameters(JOB_NAME,[name:"myName"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${BUILD_NUMBER}")], null)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${MASTER}/jobs/${JOB_NAME}")
          .contentType(MediaType.APPLICATION_JSON).param("name", "myName")).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == BUILD_NUMBER.toString()
    }

    void 'trigger a build without parameters to a job with parameters with default values'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(parameterDefinitionList: [new ParameterDefinition(defaultName: "name", defaultValue: "value", description: "description")])
        1 * jenkinsService.buildWithParameters(JOB_NAME, ['startedBy': "igor"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${BUILD_NUMBER}")], null)


        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${MASTER}/jobs/${JOB_NAME}", "")
          .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.contentAsString == BUILD_NUMBER.toString()
    }

    void 'trigger a build with parameters to a job without parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig()

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${MASTER}/jobs/${JOB_NAME}")
          .contentType(MediaType.APPLICATION_JSON).param("foo", "bar")).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.status == HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    void 'trigger a build with an invalid choice'() {
        given:
        JobConfig config = new JobConfig()
        config.parameterDefinitionList = [
            new ParameterDefinition(type: "ChoiceParameterDefinition", name: "foo", choices: ["bar", "baz"])
        ]
        1 * jenkinsService.getJobConfig(JOB_NAME) >> config

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${MASTER}/jobs/${JOB_NAME}")
            .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> [MASTER: jenkinsService]
        1 * buildMasters.map >> [MASTER: jenkinsService]
        response.status == HttpStatus.BAD_REQUEST.value()
        response.errorMessage == "`bat` is not a valid choice for `foo`. Valid choices are: bar, baz"
    }

}
