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
import com.netflix.spinnaker.igor.jenkins.JenkinsService
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.service.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

    @Shared
    MockWebServer server

    final SERVICE = 'SERVICE'
    final JENKINS_SERVICE = 'JENKINS_SERVICE'
    final TRAVIS_SERVICE = 'TRAVIS_SERVICE'
    final BUILD_NUMBER = 123
    final BUILD_ID = 654321
    final QUEUED_JOB_NUMBER = 123456
    final JOB_NAME = "job/name/can/have/slashes"
    final FILE_NAME = "test.yml"

    GenericBuild genericBuild

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
        genericBuild = GenericBuild.builder().build()
        genericBuild.number = BUILD_NUMBER
        genericBuild.id = BUILD_ID

        cache = Mock(BuildCache)
        server = new MockWebServer()

        mockMvc = MockMvcBuilders
            .standaloneSetup(new BuildController(buildServices, Optional.empty(), Optional.empty(), Optional.empty()))
            .setControllerAdvice(new GenericExceptionHandlers())
            .build()
    }

    void 'get the status of a build'() {
        given:
        1 * service.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> GenericBuild.builder().building(false).number(BUILD_NUMBER).build()

        when:
        MockHttpServletResponse response = mockMvc.perform(get("/builds/status/${BUILD_NUMBER}/${SERVICE}/${JOB_NAME}")
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
    }

    void 'get an item from the queue'() {
        given:
        1 * jenkinsService.getQueuedBuild(QUEUED_JOB_NUMBER.toString()) >> new QueuedJob(executable: [number: QUEUED_JOB_NUMBER])

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
        1 * jenkinsService.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> genericBuild
        1 * jenkinsService.getBuildProperties(JOB_NAME, genericBuild, FILE_NAME) >> {
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
        1 * travisService.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> genericBuild
        1 * travisService.getBuildProperties(JOB_NAME, genericBuild, _) >> ['foo': 'bar']

        when:
        MockHttpServletResponse response = mockMvc.perform(
            get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${TRAVIS_SERVICE}/${JOB_NAME}")
                .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == "{\"foo\":\"bar\"}"
    }
}
