package com.netflix.asgard.orca.bakery.api

import retrofit.http.POST
import rx.Observable

interface BakeService {

    @POST("/api/v1/{region}/bake")
    Observable<BakeStatus> createBake()

}