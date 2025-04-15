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

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties
import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.netflix.spinnaker.igor.wercker.WerckerService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import groovy.json.JsonSlurper
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyList
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
/**
 * tests for the info controller
 */
@SuppressWarnings(['UnnecessaryBooleanExpression', 'LineLength'])
class InfoControllerSpec extends Specification {
    MockMvc mockMvc
    BuildCache cache
    BuildServices buildServices
    GoogleCloudBuildProperties gcbProperties

    @Shared
    JenkinsService service

    @Shared
    CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()

    @Shared
    MockWebServer server

    void cleanup() {
        server.shutdown()
    }

    void setup() {
        server = new MockWebServer()
    }

    void createMocks(Map<String, BuildOperations> buildServices, List<GoogleCloudBuildProperties.Account> gcbAccounts = null) {
        cache = Mock(BuildCache)
        this.buildServices = new BuildServices()
        this.buildServices.addServices(buildServices)
        if (gcbAccounts != null) {
          this.gcbProperties = new GoogleCloudBuildProperties()
          this.gcbProperties.accounts = gcbAccounts
        }
        mockMvc = MockMvcBuilders.standaloneSetup(
            new InfoController(buildCache: cache,
                buildServices: this.buildServices,
                gcbProperties: gcbProperties))
            .build()
    }

    GoogleCloudBuildProperties.Account createGCBAccount(String name) {
      return GoogleCloudBuildProperties.Account.builder()
        .name(name)
        .project('blah')
        .build()
    }

    void 'is able to get a list of jenkins buildMasters'() {
        given:
        createMocks(['master2': null, 'build.buildServices.blah': null, 'master1': null])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/masters/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        response.contentAsString == '["build.buildServices.blah","master1","master2"]'
    }

    void 'is able to get a list of google cloud build accounts'() {
      given:
      createMocks([:], [createGCBAccount("account1"), createGCBAccount("account2")])

      when:
      MockHttpServletResponse response = mockMvc.perform(get('/buildServices')
        .accept(MediaType.APPLICATION_JSON)).andReturn().response

      then:
      def actualAccounts = new JsonSlurper().parseText(response.contentAsString).collect { it.name };
      ['account1', 'account2'] == actualAccounts

    }

    void 'buildServices return empty permissions list if no permisions have been defined'() {
      given:
      createMocks([:], [createGCBAccount("account1")])

      when:
      MockHttpServletResponse response = mockMvc.perform(get('/buildServices')
        .accept(MediaType.APPLICATION_JSON)).andReturn().response

      then:
      def actualAccounts = new JsonSlurper().parseText(response.contentAsString);
      actualAccounts.size() == 1
      actualAccounts[0].permissions == [:]

    }

    void 'buildServices returns correctly if gcb is not defined'() {
      given:
      JenkinsService jenkinsService1 = new JenkinsService('master2', null, false, Permissions.EMPTY, circuitBreakerRegistry)
      createMocks(['master2': jenkinsService1])

      when:
      MockHttpServletResponse response = mockMvc.perform(get('/buildServices')
        .accept(MediaType.APPLICATION_JSON)).andReturn().response

      then:
      def actualAccounts = new JsonSlurper().parseText(response.contentAsString);
      actualAccounts.size() == 1
      actualAccounts[0].name == 'master2'

    }

    void 'is able to get a list of buildServices with permissions'() {
        given:
        JenkinsService jenkinsService1 = new JenkinsService('jenkins-foo', null, false, Permissions.EMPTY, circuitBreakerRegistry)
        JenkinsService jenkinsService2 = new JenkinsService('jenkins-bar', null, false,
            new Permissions.Builder()
                .add(Authorization.READ, ['group-1', 'group-2'] as Set)
                .add(Authorization.WRITE, 'group-2').build(),
          circuitBreakerRegistry)
        TravisService travisService = new TravisService('travis-baz', null, null, 100, 10, emptyList(), null, null, Optional.empty(), [], null,
            new Permissions.Builder()
                .add(Authorization.READ, ['group-3', 'group-4'] as Set)
                .add(Authorization.WRITE, 'group-3').build(), false, CircuitBreakerRegistry.ofDefaults())

        GoogleCloudBuildProperties.Account gcbAccount = GoogleCloudBuildProperties.Account.builder()
          .name("gcbAccount")
          .project('blah')
          .permissions(
            new Permissions.Builder()
              .add(Authorization.READ, ['group-5', 'group-6'] as Set)
              .add(Authorization.WRITE, ['group-5'] as Set)
          ).build()

        createMocks([
            'jenkins-foo': jenkinsService1,
            'jenkins-bar': jenkinsService2,
            'travis-baz': travisService
        ], [gcbAccount])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/buildServices')
            .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .response

        then:
        def jsonResponse = new JsonSlurper().parseText(response.getContentAsString())
        jsonResponse == new JsonSlurper().parseText(
            """
            [
                {
                    "name": "jenkins-foo",
                    "buildServiceProvider": "JENKINS",
                    "permissions": {}
                },
                {
                    "name": "jenkins-bar",
                    "buildServiceProvider": "JENKINS",
                    "permissions": {
                        "READ": [
                            "group-2",
                            "group-1"
                        ],
                        "WRITE": [
                            "group-2"
                        ]
                    }
                },
                {
                    "name": "travis-baz",
                    "buildServiceProvider": "TRAVIS",
                    "permissions": {
                        "READ": [
                            "group-3",
                            "group-4"
                        ],
                        "WRITE": [
                            "group-3"
                        ]
                    }
                },
                {   "name": "gcbAccount",
                    "buildServiceProvider": "GCB",
                    "permissions": {
                        "READ": [
                            "group-6",
                            "group-5"
                        ],
                        "WRITE": [
                            "group-5"
                        ]
                    }
                }
            ]

            """.stripMargin()
        )
    }

    void 'is able to get jobs for a jenkins master'() {
        given:
        JenkinsService jenkinsService = Stub(JenkinsService)
        createMocks(['master1': jenkinsService])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        jenkinsService.getJobs() >> ['list': [
            ['name': 'job1'],
            ['name': 'job2'],
            ['name': 'job3']
        ]]
        response.contentAsString == '["job1","job2","job3"]'
    }

    void 'is able to get jobs for a jenkins master with the folders plugin'() {
        given:
        JenkinsService jenkinsService = Stub(JenkinsService)
        createMocks(['master1': jenkinsService])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        jenkinsService.getBuildServiceProvider() >> BuildServiceProvider.JENKINS
        jenkinsService.getJobs() >> ['list': [
            ['name': 'folder', 'list': [
                ['name': 'job1'],
                ['name': 'job2']
            ] ],
            ['name': 'job3']
        ]]
        response.contentAsString == '["folder/job/job1","folder/job/job2","job3"]'
    }

    void 'is able to get jobs for a travis master'() {
        given:
        TravisService travisService = Stub(TravisService)
        createMocks(['travis-master1': travisService])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/travis-master1')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        travisService.getBuildServiceProvider() >> BuildServiceProvider.TRAVIS
        1 * cache.getJobNames('travis-master1') >> ["some-job"]
        response.contentAsString == '["some-job"]'

    }

    void 'is able to get jobs for a wercker master'() {
        given:
        def werckerJob = 'myOrg/myApp/myTarget'
        WerckerService werckerService = Stub(WerckerService)
        createMocks(['wercker-master': werckerService])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/wercker-master')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        werckerService.getBuildServiceProvider() >> BuildServiceProvider.WERCKER
        werckerService.getJobs() >> [werckerJob]
        response.contentAsString == '["' + werckerJob + '"]'

    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        service = new JenkinsConfig().jenkinsService("jenkins", new JenkinsConfig().jenkinsClient(TestUtils.makeOkHttpClientConfig(), host), false, Permissions.EMPTY, circuitBreakerRegistry)
    }

    @Unroll
    void 'is able to get a job config at url #url'() {
        given:
        setResponse(getJobConfig())
        createMocks(['master1': service])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        def output = new JsonSlurper().parseText(response.contentAsString)
        output.name == 'My-Build'
        output.description == ""
        output.url == 'http://jenkins.builds.net/job/My-Build/'
        output.downstreamProjectList[0].name == 'First-Downstream-Build'
        output.firstBuild == null

        where:
        url << ['/jobs/master1/MY-JOB', '/jobs/master1/folder/job/MY-JOB']
    }

    void 'is able to get a job config where a parameter includes choices'() {
        given:
        setResponse(getJobConfigWithChoices())
        createMocks(['master1': service])

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/MY-JOB')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        def output = new JsonSlurper().parseText(response.contentAsString)
        output.name == 'My-Build'
        output.parameterDefinitionList[0].defaultParameterValue == [name: 'someParam', value: 'first']
        output.parameterDefinitionList[0].defaultName == 'someParam'
        output.parameterDefinitionList[0].defaultValue == 'first'
        output.parameterDefinitionList[0].choices == ['first', 'second']
        output.parameterDefinitionList[0].type == 'ChoiceParameterDefinition'
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
