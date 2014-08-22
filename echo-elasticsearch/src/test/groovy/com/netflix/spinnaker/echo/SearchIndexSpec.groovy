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

package com.netflix.spinnaker.echo

import com.google.gson.JsonObject
import com.netflix.spinnaker.echo.config.ElasticSearchConfig
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import groovy.json.JsonSlurper
import io.searchbox.core.Get
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

/**
 * tests for the search index
 */
class SearchIndexSpec extends Specification {

    @Shared

    @Subject
    SearchIndex searchIndex

    @Shared
    ElasticSearchConfig config

    @Shared
    JsonSlurper slurper = new JsonSlurper()

    final String GET_ALL = '''
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
    }

    void setup() {
        flushElasticSearch()
    }

    void 'events are inserted to a metadata table and events table'() {
        when:
        String key = addEvent('test', 'build', ['key1': 'value1', 'key2': 'value2']).metadata_key

        and:
        JsonObject event = searchIndex.client.execute(
            new Get.Builder(searchIndex.ES_INDEX, key).type(searchIndex.METADATA_KEY).build()
        ).jsonObject._source

        then:
        event.get('source').asString == 'test'
        event.get('type').asString == 'build'

        when: 'can derive the event details from metadata key'
        JsonObject details = searchIndex.client.execute(
            new Get.Builder(searchIndex.ES_INDEX, event.get('_content_id').asString).type(searchIndex.keyFrom('test', 'build')).build()
        ).jsonObject._source

        then:
        details.get('key1').asString == 'value1'
        details.get('key2').asString == 'value2'
    }

    void 'can retrieve an event by id'(){
        when:
        String key = addEvent('test', 'build', ['key1': 'value1', 'key2': 'value2']).content_key

        Map event = searchIndex.get('test', 'build', key)

        then:
        event.key1 == 'value1'
        event.key2 == 'value2'
    }

    void 'can search events by start date'(){
        3.times{ addEvent('test', 'build', [:])}
        long now = new Date().time
        List expectedKeys = []
        3.times{ expectedKeys << addEvent('test', 'build', [:]).content_key }

        when:
        refreshSearchIndex()
        Map searchResults = searchIndex.searchEvents(now as String, null, null, null, false)

        then:
        searchResults.total == 3
        searchResults.hits.collect{ it.get('_content_id').asString }.sort() == expectedKeys.sort()
    }

    private void flushElasticSearch() {
        if (config.client.admin().indices().exists(new IndicesExistsRequest(searchIndex.ES_INDEX)).actionGet().exists) {
            config.client.admin().indices().delete(new DeleteIndexRequest(searchIndex.ES_INDEX)).actionGet()
        }
    }

    private void refreshSearchIndex() {
        config.client.admin().indices().prepareRefresh().execute().actionGet()
    }

    private Map addEvent(source, type, content) {
        searchIndex.addToIndex(
            new Event(
                details: new Metadata(source: source, type: type),
                content: content
            )
        )
    }

}
