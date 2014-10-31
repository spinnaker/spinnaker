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

package com.netflix.spinnaker.gate.services

import retrofit.http.*

interface OortService {

  @Headers("Accept: application/json")
  @GET("/applications")
  List getApplications()

  @Headers("Accept: application/json")
  @GET("/applications/{name}")
  Map getApplication(@Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters")
  Map getClusters(@Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}")
  List getClustersForAccount(@Path("name") String name,
                   @Path("account") String account)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}")
  List getCluster(@Path("name") String name,
                  @Path("account") String account,
                  @Path("cluster") String cluster)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}")
  Map getClusterByType(@Path("name") String name,
                       @Path("account") String account,
                       @Path("cluster") String cluster,
                       @Path("type") String type)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/serverGroups/{serverGroupName}")
  List getServerGroup(@Path("name") String name,
                       @Path("account") String account,
                       @Path("cluster") String cluster,
                       @Path("type") String type,
                       @Path("serverGroupName") String serverGroupName)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/loadBalancers")
  List getClusterLoadBalancers(@Path("name") String appName,
                              @Path("account") String account,
                              @Path("cluster") String cluster,
                              @Path("type") String type)

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{name}")
  Map getLoadBalancer(@Path("provider") String provider,
                      @Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/search")
  Map search(@Query("q") String query,
             @Query("type") String type,
             @Query("platform") String platform,
             @Query("pageSize") Integer size,
             @Query("page") Integer offset)
}