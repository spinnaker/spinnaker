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
package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineSummary
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import spock.lang.Shared
import spock.lang.Specification

class GitlabCiServiceSpec extends Specification {
    @Shared
    GitlabCiClient client

    @Shared
    GitlabCiService service

    void setup() {
        client = Mock(GitlabCiClient)
        service = new GitlabCiService(client, "gitlab", null, false, false, Permissions.EMPTY)
    }

    def "verify project pagination"() {
        given:
        client.getProjects(_, _, 1) >> [new Project(pathWithNamespace: "project1")]
        client.getProjects(_, _, 2) >> [new Project(pathWithNamespace: "project2")]
        client.getProjects(_, _, 3) >> []

        when:
        def projects = service.getProjects()

        then:
        projects.size() == 2
    }

    def "verify retrieving of pipelines: too large page"() {
        when:
        service.getPipelines(new Project(), 100500)

        then:
        thrown IllegalArgumentException
    }

    def "verify retrieving of pipelines"() {
        given:
        final int PROJECT_ID = 13
        final int PAGE_SIZE = 2
        final int PIPELINE_1_ID = 3
        final int PIPELINE_2_ID = 7

        Project project = new Project(id: PROJECT_ID)
        PipelineSummary ps1 = new PipelineSummary(id: PIPELINE_1_ID)
        PipelineSummary ps2 = new PipelineSummary(id: PIPELINE_2_ID)

        when:
        List<Pipeline> pipelines = service.getPipelines(project, PAGE_SIZE)

        then:
        pipelines.size() == 2
        pipelines[0].id == PIPELINE_1_ID
        pipelines[1].id == PIPELINE_2_ID

        1 * client.getPipelineSummaries(PROJECT_ID, PAGE_SIZE) >> [ps1, ps2]
        1 * client.getPipeline(PROJECT_ID, PIPELINE_1_ID) >> new Pipeline(id: PIPELINE_1_ID)
        1 * client.getPipeline(PROJECT_ID, PIPELINE_2_ID) >> new Pipeline(id: PIPELINE_2_ID)
    }

    def "verify retriving of empty pipelines"() {
        when:
        List<Pipeline> pipelines = service.getPipelines(new Project(), 10)

        then:
        pipelines.isEmpty()

        1 * client.getPipelineSummaries(_, _) >> []
    }
}
