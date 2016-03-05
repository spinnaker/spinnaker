package com.netflix.spinnaker.igor.history

import com.netflix.spinnaker.igor.history.model.Event
import retrofit.http.Body
import retrofit.http.POST

/**
 * Posts new build executions to echo
 */
interface EchoService {
    @POST('/')
    String postEvent(@Body Event event)
}
