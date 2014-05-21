package com.netflix.spinnaker.orca.bakery.api

import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

/**
 * An interface to the Bakery's REST API. See {@link https://confluence.netflix.com/display/ENGTOOLS/Bakery+API}.
 */
interface BakeryService {

    @POST("/api/v1/{region}/bake")
    Observable<BakeStatus> createBake(@Path("region") String region, @Body Bake bake)

    @GET("/api/v1/{region}/status/{id}")
    Observable<BakeStatus> lookupStatus(@Path("region") String region, @Path("id") String id)

}