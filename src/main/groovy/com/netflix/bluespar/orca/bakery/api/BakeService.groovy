package com.netflix.bluespar.orca.bakery.api

import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

interface BakeService {

    @POST("/api/v1/{region}/bake")
    Observable<BakeStatus> createBake(@Path("region") String region)

}