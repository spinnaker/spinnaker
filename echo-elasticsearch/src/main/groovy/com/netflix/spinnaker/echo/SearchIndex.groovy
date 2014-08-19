package com.netflix.spinnaker.echo

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Event
import io.searchbox.client.JestClient
import io.searchbox.client.JestResult
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

    void addToIndex(Event event) {
        String detailsString = mapper.writeValueAsString(event.details)
        Index index = new Index.Builder(detailsString).index(ES_INDEX).type(METADATA_KEY).build()
        JestResult result = client.execute(index)

        event.content.echo_parent_event = result.getValue('_id')
        event.content.echo_event_details = event.details

        String contentString = mapper.writeValueAsString(event.content)
        String eventKey = "${event.details.source}__${event.details.type}"
        index = new Index.Builder(contentString).index(ES_INDEX).type(eventKey).build()
        client.execute(index)
    }

    String list() {
        String query = '''
            {
                "query" : {
                "match_all" : {}
            },
                "fields": ["created", "source", "type"]
            }
        '''

        Search search = new Search.Builder(query)
            .addType(METADATA_KEY)
            .addIndex(ES_INDEX)
            .build()

        SearchResult result = client.execute(search);
        result.jsonString
    }

}
