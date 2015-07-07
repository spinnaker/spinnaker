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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository

/**
 * An elastic search backed search index that provides metrics aggregation and filtering
 */
@Repository
@SuppressWarnings(['CyclomaticComplexity', 'AbcMetric'])
class SearchIndex {

    @Autowired
    JestClient client

    @SuppressWarnings(['GStringExpressionWithinString', 'PropertyName'])
    @Value('${search.index}')
    String ES_INDEX

    static final String METADATA_KEY = 'metadata'

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

    Map searchEvents(String startDate,
                     String end,
                     String source,
                     String type,
                     String organization,
                     String project,
                     String application,
                     boolean full,
                     int from,
                     int size) {

        String sizeQuery = """ "size": ${size}, """
        String fromQuery = """ "from": ${from}, """

        String sourceQuery = source ? """, { "term" : { "source" : "${source}" } }""" : ''
        String typeQuery = type ? """, { "term" : { "type" : "${type}" } }""" : ''
        String organizationQuery = organization ? """, { "term" : { "organization" : "${organization}" } }""" : ''
        String projectQuery = project ? """, { "term" : { "project" : "${project}" } }""" : ''
        String applicationQuery = application ? """, { "term" : { "application" : "${application}" } }""" : ''
        String endDateQuery = end ? """, "lt": $end""" : ''

        SearchResult result = search(METADATA_KEY,
            """
                {
                    "sort" : [
                        { "created" : {"order" : "asc"} }
                    ],
                    ${sizeQuery}
                    ${fromQuery}
                    "query" : {
                        "filtered" : {
                            "filter" : {
                                "bool" : {
                                    "must" : [
                                        {
                                            "range":{
                                                "created" : {
                                                    "gte": ${startDate}
                                                    ${endDateQuery}
                                                }
                                            }
                                        }
                                        ${typeQuery}
                                        ${sourceQuery}
                                        ${organizationQuery}
                                        ${projectQuery}
                                        ${applicationQuery}
                                    ]
                                }
                            }
                        }
                    },
                    "fields": ["_content_id", "source", "type", "application", "project", "organization", "created"]
                }
            """
        )

        [total         : result.jsonObject.hits?.get('total')?.asLong ?: 0,
         hits          : result.jsonObject.hits?.hits.collect {
             def fields = it.fields
             full ? get(
                 fields.get('source')?.asString,
                 fields.get('type')?.asString,
                 fields.get('_content_id')?.asString
             ) : [
                 application : fields.get('application')?.asString,
                 source      : fields.get('source')?.asString,
                 type        : fields.get('type')?.asString,
                 id          : fields.get('_content_id')?.asString,
                 project     : fields.get('project')?.asString,
                 organization: fields.get('organization')?.asString,
                 created     : fields.get('created')?.asString
             ]
         } ?: [],
         paginationFrom: from,
         paginationSize: size
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
        client.execute(search)
    }

    private String keyFrom(String source, String type) {
        "${source}__${type}"
    }
}
