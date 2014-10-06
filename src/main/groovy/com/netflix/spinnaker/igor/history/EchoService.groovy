package com.netflix.spinnaker.igor.history

import com.netflix.spinnaker.igor.history.model.BuildDetails
import retrofit.http.Body
import retrofit.http.POST

/**
 * Posts new build executions to echo
 */
interface EchoService {
    @POST('/')
    String postBuild(@Body BuildDetails build)
}
