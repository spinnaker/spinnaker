package com.netflix.spinnaker.echo

import com.netflix.spinnaker.echo.events.EchoEventListener
import com.netflix.spinnaker.echo.model.Event
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Adds events received into elastic search
 */
@Service
class SearchEventListener implements EchoEventListener {

    @Autowired
    SearchIndex searchIndex

    @Override
    void processEvent(Event event) {
        searchIndex.addToIndex event
    }

}
