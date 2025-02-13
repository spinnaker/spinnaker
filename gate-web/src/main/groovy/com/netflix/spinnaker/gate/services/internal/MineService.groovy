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

package com.netflix.spinnaker.gate.services.internal

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

public interface MineService {

  @GET("/canaries/{id}")
  Call<Map> showCanary(@Path("id") String canaryId)

  @POST("/canaries/{id}/generateCanaryResult")
  Call<ResponseBody> generateCanaryResult(@Path("id") String id,
                                          @Query("duration") int duration,
                                          @Query("durationUnit") String durationUnit,
                                          @Body String ignored)

  @PUT("/canaries/{id}/overrideCanaryResult/{result}")
  Call<Map> overrideCanaryResult(@Path("id") String canaryId,
                           @Path("result") String result,
                           @Query("reason") String reason,
                           @Body String ignored)

  @PUT("/canaries/{id}/end")
  Call<Map> endCanary(@Path("id") String canaryId,
                @Query("result") String result,
                @Query("reason") String reason,
                @Body String ignored)

  @GET("/canaryDeployments/{id}/canaryAnalysisHistory")
  Call<List<Map>> getCanaryAnalysisHistory(@Path("id") String canaryDeploymentId)

  @GET("/canaryConfig/names")
  Call<List<String>> getCanaryConfigNames(@Query("application") String application)


  @GET("/canaryConfigs")
  Call<List<Map>> canaryConfigsForApplication(@Query("application") String application)

}
