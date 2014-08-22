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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Event
import io.searchbox.client.JestClient
import io.searchbox.client.JestResult
import io.searchbox.core.Get
import io.searchbox.core.Index
import io.searchbox.core.Search
import io.searchbox.core.SearchResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * An elastic search backed search index that provides metrics aggregation and filtering
 */
@Repository
class SearchIndex {

    @Autowired
    JestClient client

    final String ES_INDEX = 'event_history'
    final String METADATA_KEY = 'metadata'

    ObjectMapper mapper = new ObjectMapper()

    Map addToIndex(Event event) {
        event.content._event_details = event.details
        String contentString = mapper.writeValueAsString(event.content)
        String eventKey = keyFrom(event.details.source, event.details.type)
        Index index = new Index.Builder(contentString).index(ES_INDEX).type(eventKey).build()
        JestResult result = client.execute(index)

        event.details._content_id = result.getValue('_id')

        String detailsString = mapper.writeValueAsString(event.details)
        index = new Index.Builder(detailsString).index(ES_INDEX).type(METADATA_KEY).build()
        result = client.execute(index)
        ['metadata_key': result.getValue('_id'), 'content_key': event.details._content_id]
    }

    Map get(source, type, id) {
        Get get = new Get.Builder(ES_INDEX, id).type(keyFrom(source, type)).build()
        JestResult result = client.execute(get)
        result.getSourceAsObject(Map)
    }

    Map searchEvents(String start, String end, String source, String type, boolean full) {

        SearchResult result = search(METADATA_KEY,
            """
                {
                    "query" : {
                        "range":{
                            "created" : {
                                "gte": ${start}
                                ${end ? (', "lt": ' + end) : ''}
                            }
                        }
                    },
                    "fields": ["_content_id", "source", "type"]
                }
            """
        )

        [total: result.jsonObject.hits.get('total').asLong,
         hits : result.jsonObject.hits.hits.collect {
             def fields = it.fields
             full ? get(
                 fields.get('source').asString,
                 fields.get('type').asString,
                 fields.get('_content_id').asString,
             ) : fields
         }
        ]
    }

    String directSearch(String source, String type, String query) {
        search(keyFrom(source, type), query).jsonString
    }

    String directSearchMetadata(String query) {
        search(METADATA_KEY, query).jsonString
    }

    private SearchResult search(String key, String query) {
        Search search = new Search.Builder(query)
            .addIndex(ES_INDEX)
            .addType(key)
            .build()
        SearchResult result = client.execute(search)
    }

    private String keyFrom(String source, String type) {
        "${source}__${type}"
    }
}
