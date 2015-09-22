package com.netflix.spinnaker.internal.services.internal

import retrofit.http.GET
import retrofit.http.Path

interface TideService {

  @GET('/diff/cluster/{account}/{clusterName}/')
  Map getServerGroupDiff(@Path("account") String account, @Path("clusterName") String clusterName)
}
