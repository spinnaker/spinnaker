package com.netflix.spinnaker.echo

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Event
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * An elastic search backed search index that provides metrics aggregation and filtering
 */
@Repository
class SearchIndex {

    @Autowired
    Client client

    final String ES_INDEX = 'event_history'
    final String METADATA_KEY = 'metadata'

    ObjectMapper mapper = new ObjectMapper()

    void addToIndex(Event event) {
        IndexResponse response = client.prepareIndex(ES_INDEX, METADATA_KEY)
            .setSource(mapper.writeValueAsString(event.details))
            .execute()
            .actionGet()

        event.content.echo_parent_event = response.id
        event.content.echo_event_details = event.details

        response = client.prepareIndex(ES_INDEX, "${event.details.source}__${event.details.type}")
            .setSource(mapper.writeValueAsString(event.content))
            .execute()
            .actionGet()
    }

    List<Map> list() {
        SearchResponse response = client.prepareSearch().execute().actionGet()
        response.hits.collect {
            it.sourceAsMap() + [echo_id: it.id]
        }
    }

}
