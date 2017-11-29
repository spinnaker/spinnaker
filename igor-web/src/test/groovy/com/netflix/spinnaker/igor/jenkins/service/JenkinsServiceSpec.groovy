/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins.service

import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@SuppressWarnings(['LineLength', 'DuplicateNumberLiteral'])
class JenkinsServiceSpec extends Specification {

    final String JOB_UNENCODED = 'folder/job/name with spaces'
    final String JOB_ENCODED = 'folder/job/name%20with%20spaces'

    @Shared
    JenkinsClient client

    @Shared
    JenkinsService service

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(getProjects())
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)
        service = new JenkinsService('http://my.jenkins.net', client)
    }

    void cleanup() {
        server.shutdown()
    }

    @Unroll
    void 'the "#method" method encodes the job name'() {
        when:
        if (extra_args) {
            service."${method}"(JOB_UNENCODED, *extra_args)
        } else {
            service."${method}"(JOB_UNENCODED)
        }

        then:
        if (extra_args) {
            1 * client."${method}"(JOB_ENCODED, *extra_args)
        } else {
            1 * client."${method}"(JOB_ENCODED)
        }

        where:
        method                | extra_args
        'getBuilds'           | []
        'getDependencies'     | []
        'getBuild'            | [2]
        'getGitDetails'       | [2]
        'getLatestBuild'      | []
        'getJobConfig'        | []
    }

    @Unroll
    void 'the "#method" method with empty post encodes the job name'() {
        when:
        if (extra_args) {
            service."${method}"(JOB_UNENCODED, *extra_args)
        } else {
            service."${method}"(JOB_UNENCODED)
        }

        then:
        if (extra_args) {
            1 * client."${method}"(JOB_ENCODED, *extra_args, '')
        } else {
            1 * client."${method}"(JOB_ENCODED, '')
        }

        where:
        method                | extra_args
        'build'               | []
        'buildWithParameters' | [['key': 'value']]
    }

    void 'get a list of projects with the folders plugin'() {
        given:
        def host = new JenkinsProperties.JenkinsHost(
            address: server.getUrl('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client)

        when:
        List<Project> projects = service.projects.list

        then:
        projects.size() == 3
        projects*.name == ['job1', 'job2', 'folder1/job/folder2/job/job3']
    }

    private String getProjects() {
        return '<hudson>' +
                '<job>' +
                '<name>job1</name>' +
                '<lastBuild>' +
                '<action><failCount>0</failCount><skipCount>1</skipCount><totalCount>111</totalCount><urlName>testReport</urlName></action>' +
                '<action><failCount>0</failCount><skipCount>0</skipCount><totalCount>123</totalCount><urlName>testngreports</urlName></action>' +
                '<artifact><displayPath>libs/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/libs/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<artifact><displayPath>libs/myProject-1.601.0.jar</displayPath><fileName>myProject-1.601.0.jar</fileName><relativePath>build/libs/myProject-1.601.0.jar</relativePath></artifact>' +
                '<artifact><displayPath>publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</displayPath><fileName>myProject-1.601.0-sources.jar</fileName><relativePath>build/tmp/publishMavenNebulaPublicationToDistMavenRepository/org/myProject/myProject/1.601.0/myProject-1.601.0-sources.jar</relativePath></artifact>' +
                '<building>false</building>' +
                '<duration>39238</duration>' +
                '<number>1</number>' +
                '<result>SUCCESS</result>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/job1/1/</url>' +
                '</lastBuild>' +
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
                '<name>folder1</name>' +
                '<job>' +
                '<name>folder2</name>' +
                '<job>' +
                '<name>job3</name>' +
                '<lastBuild>' +
                '<building>true</building>' +
                '<number>3</number>' +
                '<timestamp>1421717251402</timestamp>' +
                '<url>http://my.jenkins.net/job/folder1/job/folder2/job/job3/3/</url>' +
                '</lastBuild>' +
                '</job>' +
                '</job>' +
                '</job>' +
                '</hudson>'
    }
}
