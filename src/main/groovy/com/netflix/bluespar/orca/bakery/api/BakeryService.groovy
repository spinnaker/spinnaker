package com.netflix.bluespar.orca.bakery.api

import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

interface BakeryService {

    @POST("/api/v1/{region}/bake")
    Observable<BakeStatus> createBake(@Path("region") String region)

    @GET("/api/v1/{region}/status/{id}")
    Observable<BakeStatus> lookupStatus(@Path("region") String region, @Path("id") String id)

}