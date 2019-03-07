package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.front50.model.Delivery
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface Front50Service {

  @GET("/deliveries/{id}")
  suspend fun deliveryById(@Path("id") id: String): Delivery

  @POST("/deliveries")
  suspend fun createDelivery(@Body delivery: Delivery): Delivery

  @PUT("/deliveries/{id}")
  suspend fun upsertDelivery(@Path("id") id: String, @Body delivery: Delivery): Delivery

  @DELETE("/applications/{application}/deliveries/{id}")
  suspend fun deleteDelivery(@Path("application") application: String, @Path("id") id: String)
}
