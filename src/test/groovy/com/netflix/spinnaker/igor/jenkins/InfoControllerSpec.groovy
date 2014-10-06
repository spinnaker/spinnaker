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

package com.netflix.spinnaker.igor.jenkins

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll

/**
 * tests for the info controller
 */
@SuppressWarnings(['UnnecessaryBooleanExpression', 'LineLength'])
class InfoControllerSpec extends Specification {

    MockMvc mockMvc
    JenkinsCache cache
    JenkinsMasters masters

    void setup() {
        cache = Mock(JenkinsCache)
        masters = Mock(JenkinsMasters)
        mockMvc = MockMvcBuilders.standaloneSetup(new InfoController(cache: cache, masters: masters)).build()
    }

    void 'is able to get a list of jenkins masters'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/masters/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * masters.map >> ['master2': [], 'build.masters.blah': [], 'master1': []]
        response.contentAsString == '["build.masters.blah","master1","master2"]'
    }

    void 'is able to get jobs for a jenkins master'() {
        given:
        final JOBS = ['blah', 'blip', 'bum']

        when:
        MockHttpServletResponse response = mockMvc.perform(get('/jobs/master1/')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * cache.getJobNames('master1') >> JOBS
        response.contentAsString == '["blah","blip","bum"]'
    }

    @Unroll
    void 'maps typeahead results by build server, returning up to requested size: #size'() {
        given:
        final MATCHES = ['master1:blah', 'master2:blah', 'master1:blip', 'master2:bum']

        when:
        def path = size != null ? "/typeahead?q=${query}&size=${size}" : "/typeahead?q=${query}"
        MockHttpServletResponse response = mockMvc.perform(get(path)
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * cache.getTypeaheadResults(query) >> MATCHES
        response.contentAsString == result

        where:
        query   | size  || result
        'b'     | 10    || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
        'b'     | 3     || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah"]}]'
        'b'     | 1     || '[{"master":"master1","results":["blah"]}]'
        'b'     | 0     || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
        'b'     | null  || '[{"master":"master1","results":["blah","blip"]},{"master":"master2","results":["blah","bum"]}]'
    }

    void 'return empty map when no results found for typeahead request'() {
        when:
        MockHttpServletResponse response = mockMvc.perform(get('/typeahead?q=igor')
            .accept(MediaType.APPLICATION_JSON)).andReturn().response

        then:
        1 * cache.getTypeaheadResults('igor') >> []
        response.contentAsString == '[]'
    }

}
