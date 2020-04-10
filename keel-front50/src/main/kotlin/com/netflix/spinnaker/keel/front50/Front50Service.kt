package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.front50.model.Delivery
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface Front50Service {

  @GET("/deliveries/{id}")
  suspend fun deliveryById(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Delivery
}
