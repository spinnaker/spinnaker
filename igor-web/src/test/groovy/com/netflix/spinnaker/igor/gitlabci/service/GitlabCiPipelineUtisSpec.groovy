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

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus
import spock.lang.Specification

class GitlabCiPipelineUtisSpec extends Specification {
    def "convert to GenericBuild"() {

        given:
        Pipeline pipeline = new Pipeline(id: 123,
            status: PipelineStatus.success)
        String repoSlug = 'user1/project1'
        String baseUrl = 'https://gitlab.com'

        when:
        GenericBuild genericBuild = GitlabCiPipelineUtis.genericBuild(pipeline, repoSlug, baseUrl)

        then:
        !genericBuild.building
        genericBuild.result == Result.SUCCESS
        genericBuild.url == 'https://gitlab.com/user1/project1/pipelines/123'
    }
}
