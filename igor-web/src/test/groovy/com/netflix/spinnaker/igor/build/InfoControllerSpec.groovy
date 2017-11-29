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

import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildMasters
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * tests for the info controller
 */
@SuppressWarnings(['UnnecessaryBooleanExpression', 'LineLength'])
class InfoControllerSpec extends Specification {
    static {
        System.setProperty("hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds", "30000")
    }

    MockMvc mockMvc
    BuildCache cache
    BuildMasters buildMasters

    @Shared
    JenkinsService service

    @Shared
    MockWebServer server

    void cleanup() {
        server.shutdown()
    }

    void setup() {
        cache = Mock(BuildCache)
        buildMasters = Mock(BuildMasters)
        mockMvc = MockMvcBuilders.standaloneSetup(new InfoController(buildCache: cache, buildMasters: buildMasters)).build()
        server = new MockWebServer()
    }

    void 'is able to get a list of jenkins buildMasters'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/masters/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> ['master2': [], 'build.buildMasters.blah': [], 'master1': []]
        response.contentAsString == '["build.buildMasters.blah","master1","master2"]'
    }

    void 'is able to get jobs for a jenkins master'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> [ 'master1' : [ 'jobs' : [ 'list': [
            ['name': 'job1'],
            ['name': 'job2'],
            ['name': 'job3']
        ] ] ] ]
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> buildMasters.map
        response.contentAsString == '["job1","job2","job3"]'
    }

    void 'is able to get jobs for a jenkins master with the folders plugin'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> [ 'master1' : [ 'jobs' : [ 'list': [
            ['name': 'folder', 'list': [
                ['name': 'job1'],
                ['name': 'job2']
            ] ],
            ['name': 'job3']
        ] ] ] ]
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> buildMasters.map
        response.contentAsString == '["folder/job/job1","folder/job/job2","job3"]'
    }

    void 'is able to get jobs for a travis master'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/travis-master1')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.JENKINS) >> ["travis-master1": []]
        1 * buildMasters.map >> ["travis-master1": []]
        1 * cache.getJobNames('travis-master1') >> ["some-job"]
        response.contentAsString == '["some-job"]'

    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        def host = new JenkinsProperties.JenkinsHost(
            address: server.getUrl('/').toString(),
            username: 'username',
            password: 'password')
        service = new JenkinsConfig().jenkinsService("jenkins", new JenkinsConfig().jenkinsClient(host))
    }

    void 'is able to get a job config'() {
        given:
        setResponse(getJobConfig())

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> ['master2': [], 'build.buildMasters.blah': [], 'master1': service]
        response.contentAsString == '{"description":null,"displayName":"My-Build","name":"My-Build","buildable":true,"color":"red","url":"http://jenkins.builds.net/job/My-Build/","parameterDefinitionList":[{"defaultName":"pullRequestSourceBranch","defaultValue":"master","name":"pullRequestSourceBranch","description":null,"type":"StringParameterDefinition"},{"defaultName":"generation","defaultValue":"4","name":"generation","description":null,"type":"StringParameterDefinition"}],"upstreamProjectList":[{"name":"Upstream-Build","url":"http://jenkins.builds.net/job/Upstream-Build/","color":"blue"}],"downstreamProjectList":[{"name":"First-Downstream-Build","url":"http://jenkins.builds.net/job/First-Downstream-Build/","color":"blue"},{"name":"Second-Downstream-Build","url":"http://jenkins.builds.net/job/Second-Downstream-Build/","color":"blue"},{"name":"Third-Downstream-Build","url":"http://jenkins.builds.net/job/Third-Downstream-Build/","color":"red"}],"concurrentBuild":false}'
    }

    void 'is able to get a job config where a parameter includes choices'() {
        given:
        setResponse(getJobConfigWithChoices())

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> ['master2': [], 'build.buildMasters.blah': [], 'master1': service]
        response.contentAsString == '{"description":null,"displayName":"My-Build","name":"My-Build","buildable":true,"color":"red","url":"http://jenkins.builds.net/job/My-Build/","parameterDefinitionList":[' +
            '{"defaultName":"someParam","defaultValue":"first","name":"someParam","description":null,"type":"ChoiceParameterDefinition","choices":["first","second"]}' +
            '],"upstreamProjectList":[{"name":"Upstream-Build","url":"http://jenkins.builds.net/job/Upstream-Build/","color":"blue"}],"downstreamProjectList":[{"name":"First-Downstream-Build","url":"http://jenkins.builds.net/job/First-Downstream-Build/","color":"blue"},{"name":"Second-Downstream-Build","url":"http://jenkins.builds.net/job/Second-Downstream-Build/","color":"blue"},{"name":"Third-Downstream-Build","url":"http://jenkins.builds.net/job/Third-Downstream-Build/","color":"red"}],"concurrentBuild":false}'
    }

    void 'is able to get a job config with the folders plugin'() {
        given:
        setResponse(getJobConfig())

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/folder/job/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * buildMasters.map >> ['master2': [], 'build.buildMasters.blah': [], 'master1': service]
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

    private String getJobConfigWithChoices() {
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
            '<property><parameterDefinition><name>someParam</name><type>ChoiceParameterDefinition</type>' +
            '<defaultParameterValue><name>someParam</name><value>first</value></defaultParameterValue>' +
            '<description/>' +
            '<choice>first</choice><choice>second</choice>' +
            '</parameterDefinition></property>' +
            '<concurrentBuild>false</concurrentBuild>' +
            '<downstreamProject><name>First-Downstream-Build</name><url>http://jenkins.builds.net/job/First-Downstream-Build/</url><color>blue</color></downstreamProject>' +
            '<downstreamProject><name>Second-Downstream-Build</name><url>http://jenkins.builds.net/job/Second-Downstream-Build/</url><color>blue</color></downstreamProject>' +
            '<downstreamProject><name>Third-Downstream-Build</name><url>http://jenkins.builds.net/job/Third-Downstream-Build/</url><color>red</color></downstreamProject>' +
            '<scm/>' +
            '<upstreamProject><name>Upstream-Build</name><url>http://jenkins.builds.net/job/Upstream-Build/</url><color>blue</color></upstreamProject>' +
            '</freeStyleProject>'
    }

}
