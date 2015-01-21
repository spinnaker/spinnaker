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
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifactList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
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
        setResponse '''<Projects><Project webUrl="http://jenkins/job/uno/" name="uno" lastBuildLabel="1" lastBuildTime="2014-05-29T01:56:03Z" lastBuildStatus="Failure" activity="Sleeping"/><Project webUrl="http://jenkins/job/dos/" name="dos" lastBuildLabel="1" lastBuildTime="2014-05-29T01:56:03Z" lastBuildStatus="Success" activity="Sleeping"/><Project webUrl="http://jenkins/job/tres/" name="tres" lastBuildLabel="1" lastBuildTime="2014-05-29T01:56:03Z" lastBuildStatus="Failure" activity="Sleeping"/></Projects>'''

        when:
        List<Project> projects = client.projects.list

        then:
        projects.size() == 3
        projects*.name == ['uno', 'dos', 'tres']
    }

    void 'gets build details'() {
        given:
        final BUILD_NUMBER = 24
        setResponse '''<freeStyleBuild><action><cause><shortDescription>Started by upstream project "SPINNAKER-igor" build number 30</shortDescription><upstreamBuild>30</upstreamBuild><upstreamProject>SPINNAKER-igor</upstreamProject><upstreamUrl>job/SPINNAKER-igor/</upstreamUrl></cause></action><action><buildsByBranchName><originmaster><buildNumber>24</buildNumber><revision><SHA1>853b2ea2f0aecaa61f06fa731743e60575075a57</SHA1><branch><SHA1>853b2ea2f0aecaa61f06fa731743e60575075a57</SHA1><name>origin/master</name></branch></revision></originmaster></buildsByBranchName><lastBuiltRevision><SHA1>853b2ea2f0aecaa61f06fa731743e60575075a57</SHA1><branch><SHA1>853b2ea2f0aecaa61f06fa731743e60575075a57</SHA1><name>origin/master</name></branch></lastBuiltRevision><remoteUrl>git@github.com:spinnaker-netflix/mayo-nflx.git</remoteUrl><scmName></scmName></action><action></action><action></action><action></action><action></action><artifact><displayPath>mayo_1.0-h24.853b2ea_all.deb</displayPath><fileName>mayo_1.0-h24.853b2ea_all.deb</fileName><relativePath>build/distributions/mayo_1.0-h24.853b2ea_all.deb</relativePath></artifact><artifact><displayPath>dependencies.txt</displayPath><fileName>dependencies.txt</fileName><relativePath>build/reports/project/dependencies.txt</relativePath></artifact><artifact><displayPath>properties.txt</displayPath><fileName>properties.txt</fileName><relativePath>build/reports/project/properties.txt</relativePath></artifact><building>false</building><description>No longer used in test.</description><duration>231011</duration><estimatedDuration>231196</estimatedDuration><fullDisplayName>SPINNAKER-igor-netflix #24</fullDisplayName><id>2014-05-29_09-13-59</id><keepLog>false</keepLog><number>24</number><result>SUCCESS</result><timestamp>1401380039000</timestamp><url>http://builds.netflix.com/job/SPINNAKER-igor-netflix/24/</url><builtOn>ssh-dynaslave-3f220763</builtOn><changeSet><kind>git</kind></changeSet></freeStyleBuild>'''
        Build build = client.getBuild('SPINNAKER-igor-netflix', BUILD_NUMBER)

        expect:
        build.artifacts.size() == 3
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

    void 'correctly retrives downstream projects'() {
        given:
        setResponse '<freeStyleProject><action></action><action></action><action></action><action></action><action></action><action></action><action></action><name>SPINNAKER-wows</name><url>http://builds.netflix.com/job/SPINNAKER-wows/</url><downstreamProject><name>SPINNAKER-volt-netflix</name><url>http://builds.netflix.com/job/SPINNAKER-volt-netflix/</url></downstreamProject></freeStyleProject>'
        List dependencies = client.getDependencies('SPINNAKER-wows').downstreamProjects

        expect:
        dependencies.size() == 1
        dependencies[0].name == 'SPINNAKER-volt-netflix'
    }

    void 'gets build artifacts'() {
        given:
        final BUILD_NUMBER = 24
        setResponse '<freeStyleBuild><artifact><displayPath>api-4.1871-h2519.9184b37.txt</displayPath><fileName>api-4.1871-h2519.9184b37.txt</fileName><relativePath>apiweb/build/api-4.1871-h2519.9184b37.txt</relativePath></artifact><artifact><displayPath>api_4.1871-h2519.9184b37_all.txt</displayPath><fileName>api_4.1871-h2519.9184b37_all.txt</fileName><relativePath>apiweb/build/api_4.1871-h2519.9184b37_all.txt</relativePath></artifact><artifact><displayPath>deb.properties</displayPath><fileName>deb.properties</fileName><relativePath>apiweb/build/deb.properties</relativePath></artifact><artifact><displayPath>api_4.1871-h2519.9184b37_all.deb</displayPath><fileName>api_4.1871-h2519.9184b37_all.deb</fileName><relativePath>apiweb/build/distributions/api_4.1871-h2519.9184b37_all.deb</relativePath></artifact><artifact><displayPath>dependencies.lock</displayPath><fileName>dependencies.lock</fileName><relativePath>apiweb/dependencies.lock</relativePath></artifact></freeStyleBuild>'
        BuildArtifactList artifacts = client.getArtifacts('SPINNAKER-igor-netflix', BUILD_NUMBER)
        List<BuildArtifact> artifactList = artifacts.artifactList
        expect:
        artifactList.size() == 5
        artifactList[0].displayPath == 'api-4.1871-h2519.9184b37.txt'
        artifactList[0].fileName == 'api-4.1871-h2519.9184b37.txt'
        artifactList[0].relativePath == 'apiweb/build/api-4.1871-h2519.9184b37.txt'

        artifactList[4].displayPath == 'dependencies.lock'
        artifactList[4].fileName == 'dependencies.lock'
        artifactList[4].relativePath == 'apiweb/dependencies.lock'
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

}
