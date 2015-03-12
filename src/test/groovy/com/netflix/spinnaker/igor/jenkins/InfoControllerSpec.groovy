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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import spock.lang.Shared

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll

/**
 * tests for the info controller
 */
@SuppressWarnings(['UnnecessaryBooleanExpression', 'LineLength'])
class InfoControllerSpec extends Specification {

    MockMvc mockMvc
    JenkinsCache cache
    JenkinsMasters masters

    @Shared
    JenkinsClient client

    @Shared
    MockWebServer server

    void cleanup() {
        server.shutdown()
    }

    void setup() {
        cache = Mock(JenkinsCache)
        masters = Mock(JenkinsMasters)
        mockMvc = MockMvcBuilders.standaloneSetup(new InfoController(cache: cache, masters: masters)).build()
        server = new MockWebServer()
    }

    void 'is able to get a list of jenkins masters'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/masters/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * masters.map >> ['master2': [], 'build.masters.blah': [], 'master1': []]
        response.contentAsString == '["build.masters.blah","master1","master2"]'
    }

    void 'is able to get jobs for a jenkins master'() {
        given:
        final JOBS = ['blah', 'blip', 'bum']

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * masters.map >> [ 'master1' : [ projects : [ list: [
            ['name': 'blah'],
            ['name': 'blip'],
            ['name': 'bum']
        ] ] ] ]
        response.contentAsString == '["blah","blip","bum"]'
    }

    @Unroll
    void 'maps typeahead results by build server, returning up to requested size: #size'() {
        given:
        final MATCHES = ['master1:blah', 'master2:blah', 'master1:blip', 'master2:bum']

        when:
        def path = size != null ? "/typeahead?q=${query}&size=${size}" : "/typeahead?q=${query}"
        MockHttpServletResponse response = mockMvc.perform(get(path)
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * cache.getTypeaheadResults(query) >> MATCHES
        response.contentAsString == result

        where:
        query   | size  || result
        'b'     | 10    || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
        'b'     | 3     || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah"]}]'
        'b'     | 1     || '[{"master":"master1","results":["blah"]}]'
        'b'     | 0     || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
        'b'     | null  || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
    }

    void 'return empty map when no results found for typeahead request'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/typeahead?q=igor')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * cache.getTypeaheadResults('igor') >> []
        response.contentAsString == '[]'
    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.play()
        client = new JenkinsConfig().jenkinsClient(server.getUrl('/').toString(), 'username', 'password')
    }

    void 'is able to get a job config'() {
        given:
        setResponse(getJobConfig())

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * masters.map >> ['master2': [], 'build.masters.blah': [], 'master1': client]
        response.contentAsString == '{"description":null,"displayName":"My-Build","name":"My-Build","buildable":true,"color":"red","url":"http://jenkins.builds.net/job/My-Build/","parameterDefinitionList":[{"defaultName":"pullRequestSourceBranch","defaultValue":"master","name":"pullRequestSourceBranch","description":null,"type":"StringParameterDefinition"},{"defaultName":"generation","defaultValue":"4","name":"generation","description":null,"type":"StringParameterDefinition"}],"upstreamProjectList":[{"name":"Upstream-Build","url":"http://jenkins.builds.net/job/Upstream-Build/","color":"blue"}],"downstreamProjectList":[{"name":"First-Downstream-Build","url":"http://jenkins.builds.net/job/First-Downstream-Build/","color":"blue"},{"name":"Second-Downstream-Build","url":"http://jenkins.builds.net/job/Second-Downstream-Build/","color":"blue"},{"name":"Third-Downstream-Build","url":"http://jenkins.builds.net/job/Third-Downstream-Build/","color":"red"}],"concurrentBuild":false}'
    }

    private String getJobConfig() {
        return '<?xml version="1.0" encoding="UTF-8"?>' +
            '<freeStyleProject>' +
            '<description/>' +
            '<displayName>My-Build</displayName>' +
            '<name>My-Build</name>' +
            '<url>http://jenkins.builds.net/job/My-Build/</url>' +
            '<buildable>true</buildable>' +
            '<color>red</color>' +
            '<firstBuild><number>1966</number><url>http://jenkins.builds.net/job/My-Build/1966/</url></firstBuild>' +
            '<healthReport><description>Build stability: 1 out of the last 5 builds failed.</description><iconUrl>health-60to79.png</iconUrl><score>80</score></healthReport>' +
            '<inQueue>false</inQueue>' +
            '<keepDependencies>false</keepDependencies>' +
            '<lastBuild><number>2698</number><url>http://jenkins.builds.net/job/My-Build/2698/</url></lastBuild>' +
            '<lastCompletedBuild><number>2698</number><url>http://jenkins.builds.net/job/My-Build/2698/</url></lastCompletedBuild>' +
            '<lastFailedBuild><number>2698</number><url>http://jenkins.builds.net/job/My-Build/2698/</url></lastFailedBuild>' +
            '<lastStableBuild><number>2697</number><url>http://jenkins.builds.net/job/My-Build/2697/</url></lastStableBuild>' +
            '<lastSuccessfulBuild><number>2697</number><url>http://jenkins.builds.net/job/My-Build/2697/</url></lastSuccessfulBuild>' +
            '<lastUnsuccessfulBuild><number>2698</number><url>http://jenkins.builds.net/job/My-Build/2698/</url></lastUnsuccessfulBuild>' +
            '<nextBuildNumber>2699</nextBuildNumber>' +
            '<property><parameterDefinition><defaultParameterValue><name>pullRequestSourceBranch</name><value>master</value></defaultParameterValue><description/><name>pullRequestSourceBranch</name><type>StringParameterDefinition</type></parameterDefinition><parameterDefinition><defaultParameterValue><name>generation</name><value>4</value></defaultParameterValue><description/><name>generation</name><type>StringParameterDefinition</type></parameterDefinition></property>' +
            '<concurrentBuild>false</concurrentBuild>' +
            '<downstreamProject><name>First-Downstream-Build</name><url>http://jenkins.builds.net/job/First-Downstream-Build/</url><color>blue</color></downstreamProject>' +
            '<downstreamProject><name>Second-Downstream-Build</name><url>http://jenkins.builds.net/job/Second-Downstream-Build/</url><color>blue</color></downstreamProject>' +
            '<downstreamProject><name>Third-Downstream-Build</name><url>http://jenkins.builds.net/job/Third-Downstream-Build/</url><color>red</color></downstreamProject>' +
            '<scm/>' +
            '<upstreamProject><name>Upstream-Build</name><url>http://jenkins.builds.net/job/Upstream-Build/</url><color>blue</color></upstreamProject>' +
            '</freeStyleProject>'
    }

}
