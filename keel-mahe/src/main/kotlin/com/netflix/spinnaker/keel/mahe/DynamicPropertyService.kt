package com.netflix.spinnaker.keel.mahe

import com.netflix.spinnaker.keel.mahe.api.PropertyResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface DynamicPropertyService {
  @GET("/properties/app/{application}")
  suspend fun getProperties(
    @Path("application") application: String
  ): PropertyResponse
}
