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

package com.netflix.spinnaker.orca.kayenta

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import retrofit.client.Response
import retrofit.http.*

interface KayentaService {

  @POST("/canary")
  Response create(@Query("metricsAccountName") String metricsAccountName,
                  @Query("storageAccountName") String storageAccountName,
                  @Query("canaryConfigId") String canaryConfigId,
                  @Query("controlScope") String controlScope,
                  @Query("experimentScope") String experimentScope,
                  @Query("startTimeIso") String startTimeIso,
                  @Query("endTimeIso") String endTimeIso,
                  @Query("step") String step,
                  @Body Map<String, String> extendedScopeParams,
                  @Query("scoreThresholdPass") String scoreThresholdPass,
                  @Query("scoreThresholdMarginal") String scoreThresholdMarginal)

  @GET("/pipelines/{executionId}")
  Pipeline getPipelineExecution(@Path("executionId") String executionId)

  @PUT("/pipelines/{executionId}/cancel")
  Map cancelPipelineExecution(@Path("executionId") String executionId, @Body String ignored)
}
