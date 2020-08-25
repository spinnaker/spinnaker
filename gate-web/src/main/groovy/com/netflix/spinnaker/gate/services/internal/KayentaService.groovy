/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services.internal

import retrofit.client.Response
import retrofit.http.*

interface KayentaService {
  @GET("/credentials")
  List getCredentials()

  @GET("/canaryConfig")
  List getCanaryConfigs(@Query("application") String application,
                        @Query("configurationAccountName") String configurationAccountName)

  @GET("/canaryConfig/{id}")
  Map getCanaryConfig(@Path("id") String id,
                      @Query("configurationAccountName") String configurationAccountName)

  @POST("/canaryConfig")
  Map createCanaryConfig(@Body Map config,
                         @Query("configurationAccountName") String configurationAccountName)

  @PUT("/canaryConfig/{id}")
  Map updateCanaryConfig(@Path("id") String id,
                         @Body Map config,
                         @Query("configurationAccountName") String configurationAccountName)

  @DELETE("/canaryConfig/{id}")
  Response deleteCanaryConfig(@Path("id") String id,
                              @Query("configurationAccountName") String configurationAccountName)

  @GET("/metadata/metricsService")
  List listMetricsServiceMetadata(@Query("filter") String filter,
                                  @Query("metricsAccountName") String metricsAccountName)

  @GET("/judges")
  List listJudges()

  @POST("/canary")
  Map initiateCanaryWithConfig(@Body Map adhocExecutionRequest,
                               @Query("application") String application,
                               @Query("parentPipelineExecutionId") String parentPipelineExecutionId,
                               @Query("metricsAccountName") String metricsAccountName,
                               @Query("storageAccountName") String storageAccountName)

  @POST("/canary/{canaryConfigId}")
  Map initiateCanary(@Path("canaryConfigId") String canaryConfigId,
                     @Body Map executionRequest,
                     @Query("application") String application,
                     @Query("parentPipelineExecutionId") String parentPipelineExecutionId,
                     @Query("metricsAccountName") String metricsAccountName,
                     @Query("storageAccountName") String storageAccountName,
                     @Query("configurationAccountName") String configurationAccountName)

  @GET("/canary/{canaryExecutionId}")
  Map getCanaryResult(@Path("canaryExecutionId") String canaryExecutionId,
                      @Query("storageAccountName") String storageAccountName)

  @GET("/canary/executions")
  List getCanaryResultsByApplication(@Query("application") String application,
                                     @Query("limit") int limit,
                                     @Query("page") int page,
                                     @Query("statuses") String statuses,
                                     @Query("storageAccountName") String storageAccountName)

  @GET("/metricSetPairList/{metricSetPairListId}")
  List getMetricSetPairList(@Path("metricSetPairListId") metricSetPairListId,
                            @Query("accountName") String storageAccountName)
}
