/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OrcaService {

  @POST("/ops")
  @Headers("Content-Type: application/context+json", "X-SPINNAKER-USER-ORIGIN: keel")
  suspend fun orchestrate(
    @Header("X-SPINNAKER-USER") user: String,
    @Body request: OrchestrationRequest
  ): TaskRefResponse

  @POST("/orchestrate/{pipelineConfigId}")
  @Headers("Content-Type: application/context+json", "X-SPINNAKER-USER-ORIGIN: keel")
  suspend fun triggerPipeline(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("pipelineConfigId") pipelineConfigId: String,
    @Body trigger: HashMap<String, Any>
  ): TaskRefResponse

  @GET("/pipelines/{id}")
  suspend fun getPipelineExecution(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): ExecutionDetailResponse

  @GET("/tasks/{id}")
  suspend fun getOrchestrationExecution(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): ExecutionDetailResponse

  @PUT("/tasks/{id}/cancel")
  suspend fun cancelOrchestration(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  )

  @PUT("/tasks/cancel")
  suspend fun cancelOrchestrations(
    @Body taskIds: List<String>,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  )

  @GET("/executions/correlated/{correlationId}")
  suspend fun getCorrelatedExecutions(
    @Path("correlationId") correlationId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<String>

  @GET("/pipelines")
  suspend fun getExecutions(
    @Query("pipelineConfigIds") pipelineConfigIds: String? = null,
    @Query("executionIds") executionIds: String? = null,
    @Query("limit") limit: Int? = 1,
    @Query("statuses") statuses: String? = null,
    @Query("expand") expand: Boolean = false
  ): List<ExecutionDetailResponse>
}
