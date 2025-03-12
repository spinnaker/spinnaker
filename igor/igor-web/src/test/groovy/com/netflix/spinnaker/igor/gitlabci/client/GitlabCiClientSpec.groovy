/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.gitlabci.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.config.GitlabCiConfig
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

import java.text.SimpleDateFormat

class GitlabCiClientSpec extends Specification {
    @Shared
    GitlabCiClient client
    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    def "get projects"() {
        given:
        setResponse GitlabApiCannedResponses.PROJECT_LIST

        when:
        List<Project> projects = client.getProjects(false, false, 1, 100)

        then:
        projects.size() == 2

        projects[0].id == 4631786
        projects[0].pathWithNamespace == 'user1/project1'

        projects[1].id == 3057147
        projects[1].pathWithNamespace == 'user1/project2'
    }

    def "get pipelines"() {
        given:
        setResponse GitlabApiCannedResponses.PIPELINE_SUMMARIES

        when:
        List<Pipeline> pipelineSummaries = client.getPipelineSummaries("500", 100)

        then:
        pipelineSummaries.size() == 3

        pipelineSummaries[0].id == 14843843
        pipelineSummaries[1].id == 14843833
        pipelineSummaries[2].id == 14081120
    }

    def "get pipeline"() {
        given:
        setResponse GitlabApiCannedResponses.PIPELINE

        when:
        Pipeline pipeline = client.getPipeline("3057147", 14081120)

        then:
        pipeline.id == 14081120
        pipeline.ref == 'master'
        pipeline.sha == 'ab0e9eb3a105082a97d5774cceb8c1b6c4d46136'

        pipeline.createdAt == new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse('2017-11-17T21:24:14.264+0000')
        pipeline.status == PipelineStatus.success
    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'application/json')
        )
        server.start()
        client = GitlabCiConfig.gitlabCiClient(server.url('/').toString(), 'token', 3000, new ObjectMapper())
    }
}
