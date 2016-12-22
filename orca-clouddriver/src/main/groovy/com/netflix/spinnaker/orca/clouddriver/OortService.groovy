/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.clouddriver

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.QueryMap

public interface OortService {
  @GET("/applications/{app}/clusters/{account}/{cluster}/{type}")
  Response getCluster(@Path("app") String app,
                      @Path("account") String account,
                      @Path("cluster") String cluster,
                      @Path("type") String type)

  @GET("/applications/{app}/clusters/{account}/{cluster}/{type}/serverGroups/{serverGroup}")
  Response getServerGroup(@Path("app") String app,
                          @Path("account") String account,
                          @Path("cluster") String cluster,
                          @Path("serverGroup") String serverGroup,
                          @Query("region") String region,
                          @Path("type") String type)

  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}")
  Response getTargetServerGroup(@Path("app") String app,
                                @Path("account") String account,
                                @Path("cluster") String cluster,
                                @Path("cloudProvider") String cloudProvider,
                                @Path("scope") String scope,
                                @Path("target") String target)

  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}/{summaryType}")
  Map<String, Object> getServerGroupSummary(@Path("app") String app,
                                            @Path("account") String account,
                                            @Path("cluster") String cluster,
                                            @Path("cloudProvider") String cloudProvider,
                                            @Path("scope") String scope,
                                            @Path("target") String target,
                                            @Path("summaryType") String summaryType,
                                            @Query("onlyEnabled") String onlyEnabled)

  @POST("/applications/{app}/jobs/{account}/{region}/{id}")
  Response collectJob(@Path("app") String app,
                      @Path("account") String account,
                      @Path("region") String region,
                      @Path("id") String id,
                      @Body String details)

  @GET("/applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Map<String, Object> getFileContents(@Path("app") String app,
                         @Path("account") String account,
                         @Path("region") String region,
                         @Path("id") String id,
                         @Path("fileName") String fileName)


  @GET("/search")
  Response getSearchResults(@Query("q") String searchTerm,
                            @Query("type") String type,
                            @Query("platform") String platform)

  @GET("/applications/{app}")
  Response getApplication(@Path("app") String app)

  @GET("/instances/{account}/{region}/{instanceId}")
  Response getInstance(@Path("account") String account,
                       @Path("region") String region,
                       @Path("instanceId") String instanceId)

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  List<Map> getLoadBalancerDetails(@Path("provider") String provider,
                                   @Path("account") String account,
                                   @Path("region") String region,
                                   @Path("name") String name)

  @POST("/cache/{cloudProvider}/{type}")
  Response forceCacheUpdate(@Path("cloudProvider") String cloudProvider,
                            @Path("type") String type,
                            @Body Map<String, ? extends Object> data)

  @GET("/cache/{cloudProvider}/{type}")
  Collection<Map> pendingForceCacheUpdates(@Path("cloudProvider") String cloudProvider,
                                           @Path("type") String type)

  @GET("/{type}/images/{account}/{region}/{imageId}")
  List<Map> getByAmiId(@Path("type") String type,
                       @Path("account") String account,
                       @Path("region") String region,
                       @Path("imageId") imageId)

  @GET("/{type}/images/find")
  List<Map> findImage(@Path("type") String type,
                      @Query("q") String query,
                      @Query("account") String account,
                      @Query("region") String region,
                      @QueryMap Map additionalFilters)
}
