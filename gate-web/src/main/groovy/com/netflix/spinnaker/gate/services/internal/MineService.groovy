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
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
/**
 *
 * @author sthadeshwar
 */
public interface MineService {

  @POST("/canaries/{id}/generateCanaryScore")
  Response generateCanaryScore(@Path("id") String id,
                               @Query("duration") int duration,
                               @Query("durationUnit") String durationUnit)

  @GET("/canaryDeployments/{id}/canaryAnalysisHistory")
  List<Map> listCanaryAnalysisHistory(@Path("id") String canaryDeploymentId)

}