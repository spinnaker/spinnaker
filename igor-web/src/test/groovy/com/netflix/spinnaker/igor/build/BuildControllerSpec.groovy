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

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.ParameterDefinition
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
/**
 * Tests for BuildController
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildControllerSpec extends Specification {

    MockMvc mockMvc
    BuildServices buildServices
    BuildCache cache
    JenkinsService jenkinsService
    BuildOperations service
    TravisService travisService
    Map<String, BuildOperations> serviceList
    def retrySupport = Spy(RetrySupport) {
        _ * sleep(_) >> { /* do nothing */ }
    }

    @Shared
    MockWebServer server

    final SERVICE = 'SERVICE'
    final JENKINS_SERVICE = 'JENKINS_SERVICE'
    final TRAVIS_SERVICE = 'TRAVIS_SERVICE'
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
        service = Mock(BuildOperations)
        jenkinsService = Mock(JenkinsService)
        jenkinsService.getBuildServiceProvider() >> BuildServiceProvider.JENKINS
        travisService = Mock(TravisService)
        travisService.getBuildServiceProvider() >> BuildServiceProvider.TRAVIS
        buildServices = new BuildServices()
        buildServices.addServices([
            (SERVICE) : service,
            (JENKINS_SERVICE): jenkinsService,
            (TRAVIS_SERVICE): travisService,
        ])
        cache = Mock(BuildCache)
        server = new MockWebServer()

        mockMvc = MockMvcBuilders
            .standaloneSetup(new BuildController(buildServices, Optional.empty(), Optional.empty(), Optional.empty()))
            .setControllerAdvice(new GenericExceptionHandlers())
            .build()
    }

    void 'get the status of a build'() {
        given:
        1 * service.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> new GenericBuild(building: false, number: BUILD_NUMBER)

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/status/${BUILD_NUMBER}/${SERVICE}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
    }

    void 'get an item from the queue'() {
        given:
        1 * jenkinsService.queuedBuild(QUEUED_JOB_NUMBER) >> new QueuedJob(executable: [number: QUEUED_JOB_NUMBER])

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/queue/${JENKINS_SERVICE}/${QUEUED_JOB_NUMBER}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "{\"executable\":{\"number\":${QUEUED_JOB_NUMBER}},\"number\":${QUEUED_JOB_NUMBER}}"
    }

    void 'deserialize a queue response'() {
        given:
        def objectMapper = JenkinsConfig.getObjectMapper()

        when:
        def queuedJob = objectMapper.readValue("<hudson><executable><number>${QUEUED_JOB_NUMBER}</number></executable></hudson>", QueuedJob.class)

        then:
        queuedJob.number == QUEUED_JOB_NUMBER
    }

    void 'deserialize a more realistic queue response'() {
        given:
        def objectMapper = JenkinsConfig.getObjectMapper()

        when:
        def queuedJob = objectMapper.readValue(
            "<buildableItem _class=\"hudson.model.Queue\$BuildableItem\">\n" +
            "    <action _class=\"hudson.model.ParametersAction\">\n" +
            "        <parameter _class=\"hudson.model.StringParameterValue\">\n" +
            "            <name>CLUSTER_NAME</name>\n" +
            "            <value>aspera-ingestqc</value>\n" +
            "        </parameter>\n" +
            "    </action>\n" +
            "    <action _class=\"hudson.model.CauseAction\">\n" +
            "        <cause _class=\"hudson.model.Cause\$UserIdCause\">\n" +
            "            <shortDescription>Started by user buildtest</shortDescription>\n" +
            "            <userId>buildtest</userId>\n" +
            "            <userName>buildtest</userName>\n" +
            "        </cause>\n" +
            "    </action>\n" +
            "    <blocked>false</blocked>\n" +
            "    <buildable>true</buildable>\n" +
            "    <id>${QUEUED_JOB_NUMBER}</id>" +
            "    <stuck>true</stuck>" +
            "    <pending>false</pending>" +
            "</buildableItem>", QueuedJob.class)

        then:
        queuedJob.number == null
    }

    void 'get a list of builds for a job'() {
        given:
        1 * jenkinsService.getBuilds(JOB_NAME) >> [new Build(number: 111), new Build(number: 222)]

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/all/${JENKINS_SERVICE}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "[{\"building\":false,\"number\":111},{\"building\":false,\"number\":222}]"
    }

    void 'get properties of a build with a bad master'() {
        given:
        jenkinsService.getBuild(JOB_NAME, BUILD_NUMBER) >> new Build(
            number: BUILD_NUMBER, artifacts: [new BuildArtifact(fileName: "badFile.yml", relativePath: FILE_NAME)])

        expect:
        mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/badMaster/${JOB_NAME}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn().response
    }

    void 'get properties of a build with a bad filename'() {
        given:
        jenkinsService.getBuildProperties(JOB_NAME, BUILD_NUMBER, FILE_NAME) >> {
            throw new NotFoundException()
        }

        expect:
        mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${JENKINS_SERVICE}/${JOB_NAME}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn().response
    }

    void 'get properties of a travis build'() {
        given:
        1 * travisService.getBuildProperties(JOB_NAME, BUILD_NUMBER, _) >> ['foo': 'bar']

        when:
        MockHttpServletResponse response = mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${TRAVIS_SERVICE}/${JOB_NAME}")
                .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "{\"foo\":\"bar\"}"
    }

    void 'trigger a build without parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true)
        1 * jenkinsService.build(JOB_NAME) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == BUILD_NUMBER.toString()

    }

    void 'trigger a build with parameters to a job with parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true, parameterDefinitionList: [new ParameterDefinition(defaultParameterValue: [name: "name", value: null], description: "description")])
        1 * jenkinsService.buildWithParameters(JOB_NAME, [name: "myName"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
            .contentType(MediaType.APPLICATION_JSON).param("name", "myName")).andReturn().response

        then:
        response.contentAsString == BUILD_NUMBER.toString()
    }

    void 'trigger a build without parameters to a job with parameters with default values'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true, parameterDefinitionList: [new ParameterDefinition(defaultParameterValue: [name: "name", value: "value"], description: "description")])
        1 * jenkinsService.buildWithParameters(JOB_NAME, ['startedBy': "igor"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}", "")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == BUILD_NUMBER.toString()
    }

    void 'trigger a build with parameters to a job without parameters'() {
        given:
        1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true)

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
            .contentType(MediaType.APPLICATION_JSON).param("foo", "bar")).andReturn().response

        then:
        response.status == HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    void 'trigger a build with an invalid choice'() {
        given:
        JobConfig config = new JobConfig(buildable: true)
        config.parameterDefinitionList = [
            new ParameterDefinition(type: "ChoiceParameterDefinition", name: "foo", choices: ["bar", "baz"])
        ]
        1 * jenkinsService.getJobConfig(JOB_NAME) >> config

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
            .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

        then:

        response.status == HttpStatus.BAD_REQUEST.value()
        response.errorMessage == "`bat` is not a valid choice for `foo`. Valid choices are: bar, baz"
    }

    void 'trigger a disabled build'() {
        given:
        JobConfig config = new JobConfig()
        1 * jenkinsService.getJobConfig(JOB_NAME) >> config

        when:
        MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
            .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

        then:
        response.status == HttpStatus.BAD_REQUEST.value()
        response.errorMessage == "Job '${JOB_NAME}' is not buildable. It may be disabled."
    }
}
