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

import com.google.gson.JsonObject
import com.netflix.spinnaker.echo.config.ElasticSearchConfig
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import io.searchbox.core.Get
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

/**
 * tests for the search index
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'LineLength'])
class SearchIndexSpec extends Specification {

    @Shared
    @Subject
    SearchIndex searchIndex

    @Shared
    ElasticSearchConfig config

    static final String GET_ALL = '''
        {
            "query" : {
                "match_all" : {}
             },
            "fields": ["created", "source", "type"]
        }
    '''

    void setupSpec() {
        config = new ElasticSearchConfig()
        searchIndex = new SearchIndex()
        searchIndex.client = config.manufacture()
        searchIndex.ES_INDEX = 'event_history'
    }

    void setup() {
        flushElasticSearch()
    }

    void 'events are inserted to a metadata table and events table'() {
        when:
        String key = addEvent('test', 'build', null, null, null, ['key1': 'value1', 'key2': 'value2']).metadata_key

        and:
        JsonObject event = searchIndex.client.execute(
            new Get.Builder(searchIndex.ES_INDEX, key).type(searchIndex.METADATA_KEY).build()
        ).jsonObject._source

        then:
        event.get('source').asString == 'test'
        event.get('type').asString == 'build'

        when: 'can derive the event details from metadata key'
        JsonObject details = searchIndex.client.execute(
            new Get.Builder(
                searchIndex.ES_INDEX,
                event.get('_content_id').asString).type(searchIndex.keyFrom('test', 'build')).build()
        ).jsonObject._source

        then:
        details.get('key1').asString == 'value1'
        details.get('key2').asString == 'value2'
    }

    void 'retrieve an event by id'() {
        when:
        String key = addEvent('test', 'build', null, null, null, ['key': 'value1', 'key2': 'value2']).content_key

        Map event = searchIndex.get('test', 'build', key)

        then:
        event.key == 'value1'
        event.key2 == 'value2'
    }

    void 'search events by start date'() {
        3.times { addEvent('test', 'build', null, null, null, [:]) }
        long now = new Date().time
        List expectedKeys = []
        3.times { expectedKeys << addEvent('test', 'build', null, null, null, [:]).content_key }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, null, null, null, null, false, 0, 10)

        then:
        searchResults.total == 3
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'search events by end date'() {
        5.times { addEvent('test', 'build', null, null, null, [:]) }
        long now = new Date().time
        List expectedKeys = []
        5.times { expectedKeys << addEvent('test', 'build', null, null, null, [:]).content_key }
        long stop = new Date().time
        5.times { addEvent('test', 'build', null, null, null, [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, stop as String, null, null, null, null, null, false, 0, 10)

        then:
        searchResults.total == 5
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'filter event search by type'() {
        long now = new Date().time
        List expectedKeys = []
        2.times {
            expectedKeys << addEvent('test', 'yes', null, null, null, [:]).content_key
            expectedKeys << addEvent('otherSource', 'yes', null, null, null, [:]).content_key
        }
        3.times { addEvent('test', 'no', null, null, null, [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, 'yes', null, null, null, false, 0, 10)

        then:
        searchResults.total == 4
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'filter event search by source'() {
        long now = new Date().time
        List expectedKeys = []
        2.times {
            expectedKeys << addEvent('maria', 'yes', null, null, null, [:]).content_key
            expectedKeys << addEvent('maria', 'no', null, null, null, [:]).content_key
        }
        3.times { addEvent('bob', 'yes', null, null, null, [:]) }
        3.times { addEvent('steve', 'no', null, null, null, [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, 'maria', null, null, null, null, false, 0, 10)

        then:
        searchResults.total == 4
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'filter event search by organization'() {
        long now = new Date().time
        List expectedKeys = []
        2.times {
            expectedKeys << addEvent('maria', 'yes', 'spinnaker', null, null, [:]).content_key
        }
        3.times { addEvent('bob', 'yes', null, null, null, [:]) }
        3.times { addEvent('steve', 'no', 'bluespar', null, null, [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, null, 'spinnaker', null, null, false, 0, 10)

        then:
        searchResults.total == 2
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'filter event search by project'() {
        long now = new Date().time
        List expectedKeys = []
        2.times {
            expectedKeys << addEvent('maria', 'yes', 'spinnaker', 'echo', null, [:]).content_key
        }
        3.times { addEvent('bob', 'yes', 'spinnaker', 'notecho', null, [:]) }
        3.times { addEvent('steve', 'no', null, 'notecho', null, [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, null, null, 'echo', null, false, 0, 10)

        then:
        searchResults.total == 2
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'filter event search by application'() {
        long now = new Date().time
        List expectedKeys = []
        2.times {
            expectedKeys << addEvent('maria', 'yes', 'spinnaker', 'spinnaker', 'kato', [:]).content_key
        }
        3.times { addEvent('bob', 'yes', 'spinnaker', 'spinnaker', 'igor', [:]) }
        3.times { addEvent('steve', 'no', null, 'notecho', 'igor', [:]) }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, null, null, null, 'kato', false, 0, 10)

        then:
        searchResults.total == 2
        searchResults.hits*.id.containsAll expectedKeys
    }

    void 'retrieve full results from search'() {
        long now = new Date().time
        addEvent('test', 'fe', null, null, null, ['key1': 'value1'])
        addEvent('test', 'fe', null, null, null, ['key1': 'value2'])

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, 'test', 'fe', null, null, null, true, 0, 10)

        then:
        searchResults.total == 2
        searchResults.hits*.key1.sort() == ['value1', 'value2']
    }

    void 'control number of results returned'() {
        20.times {
            addEvent('test', 'type', null, null, null, [:])
        }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents('0', null, 'test', 'type', null, null, null, false, 0, number)

        then:
        searchResults.total == 20
        searchResults.hits.size() == number
        searchResults.paginationSize == number

        where:
        number << [5, 20]
    }

    void 'pagination works'() {
        10.times {
            addEvent('test', 'type', null, null, null, ['key': 'first'])
        }
        10.times {
            addEvent('test', 'type', null, null, null, ['key': 'second'])
        }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents('0', null, 'test', 'type', null, null, null, true, 0, 10)

        then:
        searchResults.total == 20
        searchResults.hits.size() == 10
        searchResults.hits*.key.unique() == ['first']
        searchResults.paginationFrom == 0

        when:
        searchResults = searchIndex.searchEvents('0', null, 'test', 'type', null, null, null, true, 11, 10)

        then:
        searchResults.hits*.key.unique() == ['second']
        searchResults.paginationFrom == 11
    }

    private void flushElasticSearch() {
        if (config.client.admin().indices().exists(
            new IndicesExistsRequest(searchIndex.ES_INDEX)
        ).actionGet().exists) {
            config.client.admin().indices().delete(new DeleteIndexRequest(searchIndex.ES_INDEX)).actionGet()
        }
    }

    private void refreshSearchIndex() {
        config.client.admin().indices().prepareRefresh().execute().actionGet()
    }

    private Map addEvent(source, type, organization, project, application, content) {
        searchIndex.addToIndex(
            new Event(
                details: new Metadata(
                    source: source,
                    type: type,
                    organization: organization,
                    project: project,
                    application: application
                ),
                content: content
            )
        )
    }

}
