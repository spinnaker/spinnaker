package com.netflix.spinnaker.keel.mahe

import com.netflix.spinnaker.keel.mahe.api.PropertyResponse
import kotlinx.coroutines.Deferred
import retrofit2.http.GET
import retrofit2.http.Path

interface DynamicPropertyService {
  @GET("/properties/app/{application}")
  fun getProperties(
    @Path("application") application: String
  ): Deferred<PropertyResponse>
}
