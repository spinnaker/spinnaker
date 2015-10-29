/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.internal.services.internal
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.PUT
import retrofit.http.Path
import retrofit.http.Query
/**
 *
 * @author sthadeshwar
 */
public interface MineService {

  @PUT("/canaries/{id}")
  Map showCanary(@Path("id") String canaryId)

  @POST("/canaries/{id}/generateCanaryResult")
  Response generateCanaryResult(@Path("id") String id,
                                @Query("duration") int duration,
                                @Query("durationUnit") String durationUnit)

  @PUT("/canaries/{id}/overrideCanaryResult/{result}")
  Map overrideCanaryResult(@Path("id") String canaryId,
                           @Path("result") String result,
                           @Query("reason") String reason)

  @PUT("/canaries/{id}/end")
  Map endCanary(@Path("id") String canaryId,
                @Query("result") String result,
                @Query("reason") String reason)

  @GET("/canaryDeployments/{id}/canaryAnalysisHistory")
  List<Map> getCanaryAnalysisHistory(@Path("id") String canaryDeploymentId)

  @GET("/canaryConfig/names")
  List<String> getCanaryConfigNames()

}
