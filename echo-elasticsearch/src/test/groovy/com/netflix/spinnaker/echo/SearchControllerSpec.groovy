/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Ensures the correct parameters are passed to the Search index
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'LineLength'])
class SearchControllerSpec extends Specification {

    MockMvc mockMvc
    @Subject
    SearchController controller

    static final String QUERY = '''{
        "match" : {
            "message" : "this is a test"
        }
    }'''

    void setup() {
        controller = new SearchController()
        controller.searchIndex = Mock(SearchIndex)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    void 'get'() {
        when:
        ResultActions resultActions = mockMvc.perform(get('/search/get/igor/build/499-5553'))

        then:
        1 * controller.searchIndex.get('igor', 'build', '499-5553')
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

    @Unroll
    void 'search events'() {
        when:
        ResultActions resultActions = mockMvc.perform(get(queryString))

        then:
        1 * controller.searchIndex.searchEvents(start, end, source, type, organization, project, application, full, from, size)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()

        where:
        queryString                                                                                                                             | end  | source | type    | organization | project     | application | full  | from | size
        '/search/events/12345'                                                                                                                  | null | null   | null    | null         | null        | null        | false | 0    | 10
        '/search/events/12345?end=25'                                                                                                           | '25' | null   | null    | null         | null        | null        | false | 0    | 10
        '/search/events/12345?source=igor'                                                                                                      | null | 'igor' | null    | null         | null        | null        | false | 0    | 10
        '/search/events/12345?type=build'                                                                                                       | null | null   | 'build' | null         | null        | null        | false | 0    | 10
        '/search/events/12345?organization=delivery'                                                                                            | null | null   | null    | 'delivery'   | null        | null        | false | 0    | 10
        '/search/events/12345?project=spinnaker'                                                                                                | null | null   | null    | null         | 'spinnaker' | null        | false | 0    | 10
        '/search/events/12345?application=kato'                                                                                                 | null | null   | null    | null         | null        | 'kato'      | false | 0    | 10
        '/search/events/12345?full=true'                                                                                                        | null | null   | null    | null         | null        | null        | true  | 0    | 10
        '/search/events/12345?from=20'                                                                                                          | null | null   | null    | null         | null        | null        | false | 20   | 10
        '/search/events/12345?size=50'                                                                                                          | null | null   | null    | null         | null        | null        | false | 0    | 50
        '/search/events/12345?type=build&source=igor&end=25&full=true&from=20&size=50&organization=delivery&project=spinnaker&application=kato' | '25' | 'igor' | 'build' | 'delivery'   | 'spinnaker' | 'kato'      | true  | 20   | 50

        start = '12345'
    }

    void 'direct search metadata'() {
        when:
        ResultActions resultActions = mockMvc.perform(
            post('/search/direct/metadata')
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUERY)
        )

        then:
        1 * controller.searchIndex.directSearchMetadata(QUERY)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

    void 'direct search keys'() {
        when:
        ResultActions resultActions = mockMvc.perform(
            post('/search/direct/igor/build')
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUERY)
        )

        then:
        1 * controller.searchIndex.directSearch('igor', 'build', QUERY)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

}
