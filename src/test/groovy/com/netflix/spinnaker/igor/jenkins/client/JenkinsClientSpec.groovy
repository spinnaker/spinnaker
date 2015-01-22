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
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.client.model.TestResults
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
        projects*.name == ['uno', 'dos', 'tres']
    }

    void 'gets build details'() {
        given:
        final BUILD_NUMBER = 24
        setResponse '''<freeStyleProject><artifact><displayPath>mayo_1.0-h24.853b2ea_all.deb</displayPath><fileName>mayo_1.0-h24.853b2ea_all.deb</fileName><relativePath>build/distributions/mayo_1.0-h24.853b2ea_all.deb</relativePath></artifact><artifact><displayPath>dependencies.txt</displayPath><fileName>dependencies.txt</fileName><relativePath>build/reports/project/dependencies.txt</relativePath></artifact><artifact><displayPath>properties.txt</displayPath><fileName>properties.txt</fileName><relativePath>build/reports/project/properties.txt</relativePath></artifact><building>false</building><description>No longer used in test.</description><duration>231011</duration><estimatedDuration>231196</estimatedDuration><fullDisplayName>SPINNAKER-igor-netflix #24</fullDisplayName><id>2014-05-29_09-13-59</id><keepLog>false</keepLog><number>24</number><result>SUCCESS</result><timestamp>1401380039000</timestamp><url>http://builds.netflix.com/job/SPINNAKER-igor-netflix/24/</url><builtOn>ssh-dynaslave-3f220763</builtOn><changeSet><kind>git</kind></changeSet></freeStyleProject>'''
        Build build = client.getBuild('SPINNAKER-igor-netflix', BUILD_NUMBER)

        expect:
        build.number == BUILD_NUMBER
        build.result == 'SUCCESS'
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
        TestResults testResults = projects.list[0].lastBuild.testResults

        expect:
        testResults.failCount == 0
        testResults.skipCount == 1
        testResults.totalCount == 111
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

    private String getBuildsWithArtifactsAndTests() {
        return '<hudson>' +
                '<job>' +
                '<name>uno</name>' +
                '<lastBuild>' +
                '<action><failCount>0</failCount><skipCount>1</skipCount><totalCount>111</totalCount></action>' +
                '<artifact><displayPath>libs/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/libs/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<artifact><displayPath>libs/myProject-1.601.0.jar</displayPath><fileName>myProject-1.601.0.jar</fileName><relativePath>build/libs/myProject-1.601.0.jar</relativePath></artifact>' +
                '<artifact><displayPath>publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<building>false</building>' +
                '<duration>39238</duration>' +
                '<number>1</number>' +
                '<result>SUCCESS</result>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/uno/1/</url>' +
                '</lastBuild>' +
                '</job>' +
                '<job>' +
                '<name>dos</name>' +
                '<lastBuild>' +
                '<action><failCount>0</failCount><skipCount>0</skipCount><totalCount>222</totalCount></action>' +
                '<artifact><displayPath>libs/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/libs/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<artifact><displayPath>libs/myProject-1.601.0.jar</displayPath><fileName>myProject-1.601.0.jar</fileName><relativePath>build/libs/myProject-1.601.0.jar</relativePath></artifact>' +
                '<artifact><displayPath>publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<building>false</building>' +
                '<duration>39238</duration>' +
                '<number>2</number>' +
                '<result>SUCCESS</result>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/dos/2/</url>' +
                '</lastBuild>' +
                '</job>' +
                '<job>' +
                '<name>tres</name>' +
                '<lastBuild>' +
                '<building>true</building>' +
                '<number>3</number>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/tres/3/</url>' +
                '</lastBuild>' +
                '</job>' +
                '</hudson>'
    }
}
