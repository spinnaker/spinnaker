package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.front50.model.Delivery
import kotlinx.coroutines.Deferred
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface Front50Service {

  @GET("/deliveries/{id}")
  fun deliveryById(@Path("id") id: String): Deferred<Delivery>

  @POST("/deliveries")
  fun createDelivery(@Body delivery: Delivery): Deferred<Delivery>

  @PUT("/deliveries/{id}")
  fun upsertDelivery(@Path("id") id: String, @Body delivery: Delivery): Deferred<Delivery>

  @DELETE("/applications/{application}/deliveries/{id}")
  fun deleteDelivery(@Path("application") application: String, @Path("id") id: String): Deferred<Unit>
}
