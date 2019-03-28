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

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@SuppressWarnings(['LineLength', 'DuplicateNumberLiteral'])
class JenkinsServiceSpec extends Specification {
    static {
        System.setProperty("hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds", "30000")
    }

    final String JOB_UNENCODED = 'folder/job/name with spaces'
    final String JOB_ENCODED = 'folder/job/name%20with%20spaces'

    @Shared
    JenkinsClient client

    @Shared
    JenkinsService service

    @Shared
    JenkinsService csrfService

    void setup() {
        client = Mock(JenkinsClient)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)
        csrfService = new JenkinsService('http://my.jenkins.net', client, true, Permissions.EMPTY)
    }

    @Unroll
    void 'the "getBuilds method encodes the job name'() {
        when:
        service.getBuilds(JOB_UNENCODED)

        then:
        1 * client.getBuilds(JOB_ENCODED) >> new BuildsList(list: [])
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
            1 * client."${method}"(JOB_ENCODED, *extra_args, '', null)
        } else {
            1 * client."${method}"(JOB_ENCODED, '', null)
        }
        0 * client.getCrumb()

        where:
        method                | extra_args
        'build'               | []
        'buildWithParameters' | [['key': 'value']]
    }

    @Unroll
    void 'the "#method" method negotiates a crumb when csrf enabled'() {
        when:
        if (extra_args) {
            service."${method}"(JOB_UNENCODED, *extra_args)
        } else {
            service."${method}"(JOB_UNENCODED)
        }

        then:
        0 * client.getCrumb()

        when:
        if (extra_args) {
            csrfService."${method}"(JOB_UNENCODED, *extra_args)
        } else {
            csrfService."${method}"(JOB_UNENCODED)
        }

        then:
        1 * client.getCrumb()

        where:
        method                | extra_args
        'build'               | []
        'buildWithParameters' | [['key': 'value']]
        'stopRunningBuild'    | [1]
        'stopQueuedBuild'     | []
    }

    void 'we can read crumbs'() {
        given:
        String jenkinsCrumbResponse = '<hudson><crumb>fb171d526b9cc9e25afe80b356e12cb7</crumb><crumbRequestField>.crumb</crumbRequestField></hudson>"}'

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsCrumbResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, true, Permissions.EMPTY)

        when:
        String crumb = service.getCrumb()

        then:
        crumb == "fb171d526b9cc9e25afe80b356e12cb7"

        cleanup:
        server.shutdown()
    }

    void 'get a list of projects with the folders plugin'() {
        given:
        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(getProjects())
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        def host = new JenkinsProperties.JenkinsHost(
            address: server.getUrl('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<Project> projects = service.projects.list

        then:
        projects.size() == 3
        projects*.name == ['job1', 'job2', 'folder1/job/folder2/job/job3']

        cleanup:
        server.shutdown()
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


    void "when Jenkins returns a single scm, our JenkinsService will return a single scm"() {
        given:
        String jenkinsSCMResponse = "<freeStyleBuild _class=\"hudson.model.FreeStyleBuild\">\n" +
            "<action _class=\"hudson.plugins.git.util.BuildData\">\n" +
            "<lastBuiltRevision>\n" +
            "<branch>\n" +
            "<SHA1>111aaa</SHA1>\n" +
            "<name>refs/remotes/origin/master</name>\n" +
            "</branch>\n" +
            "</lastBuiltRevision>\n" +
            "<remoteUrl>https://github.com/spinnaker/igor</remoteUrl>\n" +
            "</action>\n" +
            "</freeStyleBuild>"

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 1
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        cleanup:
        server.shutdown()
    }

    void "when Jenkins returns multiple scms, our JenkinsService will return multiple scms"() {
        given:
        String jenkinsSCMResponse = "<freeStyleBuild _class=\"hudson.model.FreeStyleBuild\">\n" +
            "<action _class=\"hudson.plugins.git.util.BuildData\">\n" +
            "<lastBuiltRevision>\n" +
            "<branch>\n" +
            "<SHA1>111aaa</SHA1>\n" +
            "<name>refs/remotes/origin/master</name>\n" +
            "</branch>\n" +
            "</lastBuiltRevision>\n" +
            "<remoteUrl>https://github.com/spinnaker/igor</remoteUrl>\n" +
            "</action>\n" +
            "<action _class=\"hudson.plugins.git.util.BuildData\">\n" +
            "<lastBuiltRevision>\n" +
            "<branch>\n" +
            "<SHA1>222bbb</SHA1>\n" +
            "<name>refs/remotes/origin/master-master</name>\n" +
            "</branch>\n" +
            "</lastBuiltRevision>\n" +
            "<remoteUrl>https://github.com/spinnaker/igor-fork</remoteUrl>\n" +
            "</action>\n" +
            "</freeStyleBuild>"

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 2
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        genericGitRevision.get(1).name == "refs/remotes/origin/master-master"
        genericGitRevision.get(1).branch == "master-master"
        genericGitRevision.get(1).sha1 == "222bbb"
        genericGitRevision.get(1).remoteUrl == "https://github.com/spinnaker/igor-fork"

        cleanup:
        server.shutdown()
    }

    void "when Jenkins returns a single scm in BuildDetails, our JenkinsService will return a single scm"() {
        given:
        String jenkinsSCMResponse = String.join("\n",
            '<freeStyleBuild _class="hudson.model.FreeStyleBuild">',
            '  <action _class=\"hudson.plugins.git.util.BuildDetails\">',
            '    <build>',
            '      <revision>',
            '        <branch>',
            '          <SHA1>111aaa</SHA1>',
            '          <name>refs/remotes/origin/master</name>',
            '        </branch>',
            '      </revision>',
            '    </build>',
            '    <remoteUrl>https://github.com/spinnaker/igor</remoteUrl>',
            '  </action>',
            '</freeStyleBuild>',
        )

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 1
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        cleanup:
        server.shutdown()
    }

    void "when Jenkins returns multiple scms in BuildDetails, our JenkinsService will return multiple scms"() {
        given:
        String jenkinsSCMResponse = String.join("\n",
            '<freeStyleBuild _class="hudson.model.FreeStyleBuild">',
            '  <action _class=\"hudson.plugins.git.util.BuildDetails\">',
            '    <build>',
            '      <revision>',
            '        <branch>',
            '          <SHA1>111aaa</SHA1>',
            '          <name>refs/remotes/origin/master</name>',
            '        </branch>',
            '      </revision>',
            '    </build>',
            '    <remoteUrl>https://github.com/spinnaker/igor</remoteUrl>',
            '  </action>',
            '  <action _class=\"hudson.plugins.git.util.BuildData\">',
            '    <build>',
            '      <revision>',
            '        <branch>',
            '          <SHA1>222bbb</SHA1>',
            '          <name>refs/remotes/origin/master-master</name>',
            '        </branch>',
            '      </revision>',
            '    </build>',
            '    <remoteUrl>https://github.com/spinnaker/igor-fork</remoteUrl>',
            '  </action>',
            '</freeStyleBuild>',
        )

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 2
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        genericGitRevision.get(1).name == "refs/remotes/origin/master-master"
        genericGitRevision.get(1).branch == "master-master"
        genericGitRevision.get(1).sha1 == "222bbb"
        genericGitRevision.get(1).remoteUrl == "https://github.com/spinnaker/igor-fork"

        cleanup:
        server.shutdown()
    }

    void "when Jenkins returns different scms in BuildData and BuildDetails, both are returned"() {
        given:
        String jenkinsSCMResponse = String.join("\n",
            '<freeStyleBuild _class="hudson.model.FreeStyleBuild">',
            '  <action _class=\"hudson.plugins.git.util.BuildData\">',
            '    <lastBuiltRevision>',
            '      <branch>',
            '        <SHA1>111aaa</SHA1>',
            '        <name>refs/remotes/origin/master</name>',
            '      </branch>',
            '    </lastBuiltRevision>',
            '    <remoteUrl>https://github.com/spinnaker/igor</remoteUrl>',
            '  </action>',
            '  <action _class=\"hudson.plugins.git.util.BuildData\">',
            '    <build>',
            '      <revision>',
            '        <branch>',
            '          <SHA1>222bbb</SHA1>',
            '          <name>refs/remotes/origin/master-master</name>',
            '        </branch>',
            '      </revision>',
            '    </build>',
            '    <remoteUrl>https://github.com/spinnaker/igor-fork</remoteUrl>',
            '  </action>',
            '</freeStyleBuild>',
        )

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 2
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        genericGitRevision.get(1).name == "refs/remotes/origin/master-master"
        genericGitRevision.get(1).branch == "master-master"
        genericGitRevision.get(1).sha1 == "222bbb"
        genericGitRevision.get(1).remoteUrl == "https://github.com/spinnaker/igor-fork"

        cleanup:
        server.shutdown()
    }

    void "when Jenkins the same scm in BuildData and BuildDetails, it is returned only once"() {
        given:
        String jenkinsSCMResponse = String.join("\n",
            '<freeStyleBuild _class="hudson.model.FreeStyleBuild">',
            '  <action _class=\"hudson.plugins.git.util.BuildData\">',
            '    <lastBuiltRevision>',
            '      <branch>',
            '        <SHA1>111aaa</SHA1>',
            '        <name>refs/remotes/origin/master</name>',
            '      </branch>',
            '    </lastBuiltRevision>',
            '    <remoteUrl>https://github.com/spinnaker/igor</remoteUrl>',
            '  </action>',
            '  <action _class=\"hudson.plugins.git.util.BuildData\">',
            '    <build>',
            '      <revision>',
            '        <branch>',
            '          <SHA1>111aaa</SHA1>',
            '          <name>refs/remotes/origin/master</name>',
            '        </branch>',
            '      </revision>',
            '    </build>',
            '    <remoteUrl>https://github.com/spinnaker/igor</remoteUrl>',
            '  </action>',
            '</freeStyleBuild>',
        )

        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(jenkinsSCMResponse)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = Mock(JenkinsClient)

        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        when:
        List<GenericGitRevision> genericGitRevision = service.getGenericGitRevisions('test', 1)

        then:
        genericGitRevision.size() == 1
        genericGitRevision.get(0).name == "refs/remotes/origin/master"
        genericGitRevision.get(0).branch == "master"
        genericGitRevision.get(0).sha1 == "111aaa"
        genericGitRevision.get(0).remoteUrl == "https://github.com/spinnaker/igor"

        cleanup:
        server.shutdown()
    }

    @Unroll
    def "getProperties correctly deserializes properties}"() {
        given:
        def extension = testCase.extension
        String buildData = String.join("\n",
            '<freeStyleBuild _class="hudson.model.FreeStyleBuild">',
                '<artifact>',
                    "<displayPath>props$extension</displayPath>",
                    "<fileName>props$extension</fileName>",
                    "<relativePath>properties/props$extension</relativePath>",
                '</artifact>',
                '<building>false</building>',
                '<duration>341</duration>',
                '<fullDisplayName>PropertiesTest #5</fullDisplayName>',
                '<number>5</number>',
                '<result>SUCCESS</result>',
                '<timestamp>1551546969642</timestamp>',
                '<url>http://jenkins-host.test/job/PropertiesTest/5/</url>',
            '</freeStyleBuild>')
        MockWebServer server = new MockWebServer()
        server.enqueue(
            new MockResponse()
                .setBody(buildData)
                .setHeader('Content-Type', "application/xml")
        )
        server.enqueue(
            new MockResponse()
                .setBody(testCase.contents)
                .setHeader('Content-Type', "application/octet-stream")
        )
        server.start()
        def host = new JenkinsProperties.JenkinsHost(
            address: server.url('/').toString(),
            username: 'username',
            password: 'password')
        client = new JenkinsConfig().jenkinsClient(host)
        service = new JenkinsService('http://my.jenkins.net', client, false, Permissions.EMPTY)

        expect:
        service.getBuildProperties("PropertiesTest", 1, "props$extension") == testCase.result

        cleanup:
        server.shutdown()

        where:
        testCase << [
            [
                extension: "",
                contents: '''
                    a=hello
                    b=world
                    c=3
                ''',
                result: [a: "hello", b: "world", c: "3"],
            ],
            [
                extension: ".json",
                contents: '''
                    {
                        "a" : "hello",
                        "b" : "world",
                        "c" : 3,
                        "nested" : {
                            "list" : [1, "a"]
                        }
                    }
                ''',
                result: [a: "hello", b: "world", c: 3, nested: [list: [1, "a"]] ],
            ],
            [
                extension: ".yml",
                contents: '''
                    a: hello
                    b: world
                    c: 3
                    nested:
                        list:
                            - 1
                            - a
                ''',
                result: [a: "hello", b: "world", c: 3, nested: [list: [1, "a"]] ],
            ],
        ]
    }

}
