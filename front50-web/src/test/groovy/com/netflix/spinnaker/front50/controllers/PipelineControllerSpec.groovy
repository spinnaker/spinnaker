/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.controllers

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.pipeline.PipelineRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

class PipelineControllerSpec extends Specification {

    static final int OK = 200

    MockMvc mockMvc
    PipelineRepository pipelineRepository

    void setup() {
        pipelineRepository = Mock(PipelineRepository)
        mockMvc = MockMvcBuilders.standaloneSetup(
            new PipelineController(pipelineRepository: pipelineRepository)).build()
    }

    void 'return 200 for successful rename'() {
        given:
        def command = [
            application: 'test',
            from: 'old-pipeline-name',
            to: 'new-pipeline-name'
        ]

        when:
        def response = mockMvc.perform(post('/pipelines/move').
            contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(command)))
            .andReturn().response

        then:
        response.status == OK
        1 * pipelineRepository.rename('test', 'old-pipeline-name', 'new-pipeline-name')
    }

}
