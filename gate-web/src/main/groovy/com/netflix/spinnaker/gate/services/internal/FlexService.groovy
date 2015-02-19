package com.netflix.spinnaker.gate.services.internal

import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface FlexService {
  @GET("/applications/{application}/clusters/{account}/{cluster}/elasticIps")
  List<Map> getForCluster(@Path("application") String application,
                          @Path("account") String account,
                          @Path("cluster") String cluster)

  @GET("/applications/{application}/clusters/{account}/{cluster}/elasticIps/{region}")
  List<Map> getForClusterAndRegion(@Path("application") String application,
                                   @Path("account") String account,
                                   @Path("cluster") String cluster,
                                   @Path("region") String region)

  @GET("/elasticIps/{account}")
  List<Map> getForAccount(@Path("account") String account)

  @GET("/elasticIps/{account}")
  List<Map> getForAccountAndRegion(@Path("account") String account, @Query("region") String region)
}
