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

package com.netflix.spinnaker.igor.jenkins.client

import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.model.Crumb
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
/**
 * Tests that Jenkins client correctly binds to underlying XML model as expected
 */
@SuppressWarnings(['LineLength', 'DuplicateNumberLiteral'])
class JenkinsClientSpec extends Specification {

    @Shared
    JenkinsClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    void 'get a list of projects from the jenkins service'() {
        given:
        setResponse getBuildsWithArtifactsAndTests()

        when:
        List<Project> projects = client.projects.list

        then:
        projects.size() == 3
        projects*.name == ['job1', 'job2', 'folder1/job/folder2/job/job3']
    }

    void 'gets build details'() {
        given:
        final BUILD_NUMBER = 24
        setResponse '''<freeStyleProject><artifact><displayPath>mayo_1.0-h24.853b2ea_all.deb</displayPath><fileName>mayo_1.0-h24.853b2ea_all.deb</fileName><relativePath>build/distributions/mayo_1.0-h24.853b2ea_all.deb</relativePath></artifact><artifact><displayPath>dependencies.txt</displayPath><fileName>dependencies.txt</fileName><relativePath>build/reports/project/dependencies.txt</relativePath></artifact><artifact><displayPath>igorProperties.txt</displayPath><fileName>igorProperties.txt</fileName><relativePath>build/reports/project/igorProperties.txt</relativePath></artifact><building>false</building><description>No longer used in test.</description><duration>231011</duration><estimatedDuration>231196</estimatedDuration><fullDisplayName>SPINNAKER-igor-netflix #24</fullDisplayName><id>2014-05-29_09-13-59</id><keepLog>false</keepLog><number>24</number><result>SUCCESS</result><timestamp>1401380039000</timestamp><url>http://builds.netflix.com/job/SPINNAKER-igor-netflix/24/</url><builtOn>ssh-dynaslave-3f220763</builtOn><changeSet><kind>git</kind></changeSet></freeStyleProject>'''
        Build build = client.getBuild('SPINNAKER-igor-netflix', BUILD_NUMBER)

        expect:
        build.number == BUILD_NUMBER
        build.result == 'SUCCESS'
    }

    void 'gets crumb'() {
        given:
        setResponse '<defaultCrumbIssuer _class=\'hudson.security.csrf.DefaultCrumbIssuer\'><crumb>2f70a60a9f993597a565862020bedd5a</crumb><crumbRequestField>Jenkins-Crumb</crumbRequestField></defaultCrumbIssuer>'
        Crumb crumb = client.getCrumb()

        expect:
        crumb.crumb == '2f70a60a9f993597a565862020bedd5a'
        crumb.crumbRequestField == 'Jenkins-Crumb'
    }

    void 'correctly retrieves upstream dependencies'() {
        given:
        setResponse '<freeStyleProject><action></action><action></action><action></action><action></action><action></action><action></action><name>SPINNAKER-volt-netflix</name><url>http://builds.netflix.com/job/SPINNAKER-volt-netflix/</url><upstreamProject><name>SPINNAKER-volt</name><url>http://builds.netflix.com/job/SPINNAKER-volt/</url></upstreamProject><upstreamProject><name>SPINNAKER-wows</name><url>http://builds.netflix.com/job/SPINNAKER-wows/</url></upstreamProject></freeStyleProject>'
        List dependencies = client.getDependencies('SPINNAKER-volt-netflix').upstreamProjects

        expect:
        dependencies.size() == 2
        dependencies*.name.sort() == ['SPINNAKER-volt', 'SPINNAKER-wows']
    }

    void 'correctly retrieves downstream projects'() {
        given:
        setResponse '<freeStyleProject><action></action><action></action><action></action><action></action><action></action><action></action><action></action><name>SPINNAKER-wows</name><url>http://builds.netflix.com/job/SPINNAKER-wows/</url><downstreamProject><name>SPINNAKER-volt-netflix</name><url>http://builds.netflix.com/job/SPINNAKER-volt-netflix/</url></downstreamProject></freeStyleProject>'
        List dependencies = client.getDependencies('SPINNAKER-wows').downstreamProjects

        expect:
        dependencies.size() == 1
        dependencies[0].name == 'SPINNAKER-volt-netflix'
    }

    void 'gets build artifacts'() {
        given:
        setResponse getBuildsWithArtifactsAndTests()
        ProjectsList projects = client.getProjects()
        List<BuildArtifact> artifactList = projects.list[0].lastBuild.artifacts
        expect:
        artifactList.size() == 3
        artifactList[0].displayPath == 'libs/myProject-1.601.0-sources.jar'
        artifactList[0].fileName == 'myProject-1.601.0-sources.jar'
        artifactList[0].relativePath == 'build/libs/myProject-1.601.0-sources.jar'

        artifactList[2].displayPath == 'publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar'
        artifactList[2].fileName == 'myProject-1.601.0-sources.jar'
        artifactList[2].relativePath == 'build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar'
    }

    void 'gets test results'() {
        given:
        setResponse getBuildsWithArtifactsAndTests()
        ProjectsList projects = client.getProjects()
        List testResults = projects.list[0].lastBuild.testResults

        expect:
        testResults.size() == 2
        testResults[0].failCount == 0
        testResults[0].skipCount == 1
        testResults[0].totalCount == 111
        testResults[0].urlName == 'testReport'

        testResults[1].failCount == 0
        testResults[1].skipCount == 0
        testResults[1].totalCount == 123
        testResults[1].urlName == 'testngreports'
    }

    void 'gets a single build'() {
        given:
        setResponse getSingleBuild()
        Build build = client.getBuild("FOO",2542)

        expect:
        build.artifacts.size() == 4
        !build.building
        build.duration == 532271
        build.number == 2542
        build.result == 'SUCCESS'
        build.timestamp == "1421961940704"
        build.url == "http:///my.jenkins.net/job/FOO/2542/"
        build.testResults[0].failCount == 0
        build.testResults[0].skipCount == 9
        build.testResults[0].totalCount == 465
        build.testResults[0].urlName == 'testReport'
    }

    void 'get a job config'() {
        given:
        setResponse getJobConfig()
        JobConfig jobConfig = client.getJobConfig("FOO-JOB")

        expect:
        jobConfig.name == "My-Build"
        jobConfig.buildable == true
        jobConfig.color == "red"
        jobConfig.concurrentBuild == false
        jobConfig.description == null
        jobConfig.displayName == "My-Build"
        jobConfig.parameterDefinitionList?.size() == 2
        jobConfig.parameterDefinitionList.get(0).name == "pullRequestSourceBranch"
        jobConfig.parameterDefinitionList.get(0).description == null
        jobConfig.parameterDefinitionList.get(0).defaultName == "pullRequestSourceBranch"
        jobConfig.parameterDefinitionList.get(0).defaultValue == "master"
        jobConfig.parameterDefinitionList.get(1).name == "generation"
        jobConfig.parameterDefinitionList.get(1).description == null
        jobConfig.parameterDefinitionList.get(1).defaultName == "generation"
        jobConfig.parameterDefinitionList.get(1).defaultValue == "4"

        jobConfig.upstreamProjectList.get(0).name == "Upstream-Build"
        jobConfig.upstreamProjectList.get(0).url == "http://jenkins.builds.net/job/Upstream-Build/"
        jobConfig.upstreamProjectList.get(0).color == "blue"

        jobConfig.downstreamProjectList.get(0).name == "First-Downstream-Build"
        jobConfig.downstreamProjectList.get(0).url == "http://jenkins.builds.net/job/First-Downstream-Build/"
        jobConfig.downstreamProjectList.get(0).color == "blue"

        jobConfig.downstreamProjectList.get(1).name == "Second-Downstream-Build"
        jobConfig.downstreamProjectList.get(1).url == "http://jenkins.builds.net/job/Second-Downstream-Build/"
        jobConfig.downstreamProjectList.get(1).color == "blue"

        jobConfig.downstreamProjectList.get(2).name == "Third-Downstream-Build"
        jobConfig.downstreamProjectList.get(2).url == "http://jenkins.builds.net/job/Third-Downstream-Build/"
        jobConfig.downstreamProjectList.get(2).color == "red"

        jobConfig.url == "http://jenkins.builds.net/job/My-Build/"

    }

    void 'trigger a build without parameters'() {
        given:
        setResponse("")

        when:
        def response = client.build("My-Build", "", jenkinsCrumb)

        then:
        response

        where:
        jenkinsCrumb << [null, 'crumb']
    }

    void 'trigger a build with parameters'() {
        given:
        setResponse("")

        when:
        def response = client.buildWithParameters("My-Build", [foo:"bar", key:"value"], "", jenkinsCrumb)

        then:
        response

        where:
        jenkinsCrumb << [null, 'crumb']
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
        client = new JenkinsConfig().jenkinsClient(host)
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

    private String getSingleBuild() {
        return '<?xml version="1.0" encoding="UTF-8"?>' +
                '<freeStyleBuild>' +
                '<action><failCount>0</failCount><skipCount>9</skipCount><totalCount>465</totalCount><urlName>testReport</urlName></action>' +
                '<artifact><displayPath>api.txt</displayPath><fileName>api.txt</fileName><relativePath>apiweb/build/api.txt</relativePath></artifact>' +
                '<artifact><displayPath>deb.igorProperties</displayPath><fileName>deb.igorProperties</fileName><relativePath>foo/build/deb.igorProperties</relativePath></artifact>' +
                '<artifact><displayPath>api.deb</displayPath><fileName>api.deb</fileName><relativePath>foo/build/distributions/api.deb</relativePath></artifact>' +
                '<artifact><displayPath>dependencies.lock</displayPath><fileName>dependencies.lock</fileName><relativePath>foo/dependencies.lock</relativePath></artifact>' +
                '<building>false</building>' +
                '<duration>532271</duration>' +
                '<number>2542</number>' +
                '<result>SUCCESS</result>' +
                '<timestamp>1421961940704</timestamp>' +
                '<url>http:///my.jenkins.net/job/FOO/2542/</url>' +
                '</freeStyleBuild>'
    }

    private String getBuildsWithArtifactsAndTests() {
        return '<hudson>' +
                '<job>' +
                ' <name>job1</name>' +
                ' <lastBuild>' +
                '   <action><failCount>0</failCount><skipCount>1</skipCount><totalCount>111</totalCount><urlName>testReport</urlName></action>' +
                '   <action><failCount>0</failCount><skipCount>0</skipCount><totalCount>123</totalCount><urlName>testngreports</urlName></action>' +
                '   <artifact><displayPath>libs/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/libs/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '   <artifact><displayPath>libs/myProject-1.601.0.jar</displayPath><fileName>myProject-1.601.0.jar</fileName><relativePath>build/libs/myProject-1.601.0.jar</relativePath></artifact>' +
                '   <artifact><displayPath>publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '   <building>false</building>' +
                '   <duration>39238</duration>' +
                '   <number>1</number>' +
                '   <result>SUCCESS</result>' +
                '   <timestamp>1421717251402</timestamp>' +
                '   <url>http://my.jenkins.net/job/job1/1/</url>' +
                ' </lastBuild>' +
                '</job>' +
                '<job>' +
                '<name>job2</name>' +
                '<lastBuild>' +
                '<action><failCount>0</failCount><skipCount>0</skipCount><totalCount>222</totalCount></action>' +
                '<action><failCount>0</failCount><skipCount>0</skipCount><totalCount>222</totalCount></action>' +
                '<artifact><displayPath>libs/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/libs/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<artifact><displayPath>libs/myProject-1.601.0.jar</displayPath><fileName>myProject-1.601.0.jar</fileName><relativePath>build/libs/myProject-1.601.0.jar</relativePath></artifact>' +
                '<artifact><displayPath>publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<building>false</building>' +
                '<duration>39238</duration>' +
                '<number>2</number>' +
                '<result>SUCCESS</result>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/job2/2/</url>' +
                '</lastBuild>' +
                '</job>' +
                '<job>' +
                '<name>folder1/job/folder2/job/job3</name>' +
                '<lastBuild>' +
                '<building>true</building>' +
                '<number>3</number>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/folder1/job/folder2/job/job3/3/</url>' +
                '</lastBuild>' +
                '</job>' +
                '</hudson>'
    }

    private String getCrumb() {
        return '<defaultCrumbIssuer _class=\'hudson.security.csrf.DefaultCrumbIssuer\'>' +
            '<crumb>' +
            '2f70a60a9f993597a565862020bedd5a' +
            '</crumb>' +
            '<crumbRequestField>' +
            'Jenkins-Crumb' +
            '</crumbRequestField>' +
            '</defaultCrumbIssuer>'
    }
}
