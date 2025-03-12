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
import com.netflix.spinnaker.igor.config.GitlabCiProperties
import com.netflix.spinnaker.igor.config.GitlabCiProperties.GitlabCiHost
import com.netflix.spinnaker.igor.gitlabci.client.GitlabApiCannedResponses
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import spock.lang.Shared
import spock.lang.Specification

class GitlabCiServiceSpec extends Specification {
    @Shared
    GitlabCiClient client

    @Shared
    GitlabCiService service

    @Shared
    GitlabCiHost hostConfig

    void setup() {
        client = Mock(GitlabCiClient)
        hostConfig = new GitlabCiHost()
        service = new GitlabCiService(client, "gitlab", hostConfig, Permissions.EMPTY)
    }

    def "verify invalid config of http page size"() {
      when:
      hostConfig.setDefaultHttpPageLength(999999999)

      then:
      thrown IllegalArgumentException
    }

    def "verify project pagination"() {
        given:
        client.getProjects(_, _, 1, _) >> [new Project(pathWithNamespace: "project1", buildsAccessLevel: "enabled")]
        client.getProjects(_, _, 2, _) >> [new Project(pathWithNamespace: "project2", buildsAccessLevel: "enabled")]
        client.getProjects(_, _, 3, _) >> []

        when:
        def projects = service.getProjects()

        then:
        projects.size() == 2
    }

    def "verify retrieving of pipelines"() {
        given:
        final int PROJECT_ID = 13
        final int PAGE_SIZE = 2
        final int PIPELINE_1_ID = 3
        final int PIPELINE_2_ID = 7
        Project project = new Project(id: PROJECT_ID)
        Pipeline ps1 = new Pipeline(id: PIPELINE_1_ID)
        Pipeline ps2 = new Pipeline(id: PIPELINE_2_ID)
        client.getPipelineSummaries(String.valueOf(PROJECT_ID), PAGE_SIZE) >> [ps1, ps2]
        client.getPipeline(String.valueOf(PROJECT_ID), PIPELINE_1_ID) >> new Pipeline(id: PIPELINE_1_ID)
        client.getPipeline(String.valueOf(PROJECT_ID), PIPELINE_1_ID) >> new Pipeline(id: PIPELINE_1_ID)

        when:
        List<Pipeline> pipelines = service.getPipelines(project, PAGE_SIZE)

        then:
        pipelines.size() == 2
        pipelines[0].id == PIPELINE_1_ID
        pipelines[1].id == PIPELINE_2_ID
    }

    def "verify retrieving of empty pipelines"() {
        when:
        List<Pipeline> pipelines = service.getPipelines(new Project(), 10)

        then:
        pipelines.isEmpty()

        1 * client.getPipelineSummaries(_, _) >> []
    }
}
