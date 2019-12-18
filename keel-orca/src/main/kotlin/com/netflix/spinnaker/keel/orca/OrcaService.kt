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

import com.netflix.spinnaker.keel.model.OrchestrationRequest
import java.time.Instant
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

// TODO Origin needs to be set on executions
// origin is used for dynamic routing by orca to different clouddriver instances
interface OrcaService {

  @POST("/ops")
  @Headers("Content-Type: application/context+json")
  suspend fun orchestrate(@Header("X-SPINNAKER-USER") serviceAccount: String, @Body request: OrchestrationRequest):
    TaskRefResponse

  @POST("/orchestrate/{pipelineConfigId}")
  @Headers("Content-Type: application/context+json")
  suspend fun triggerPipeline(
    @Header("X-SPINNAKER-USER") serviceAccount: String,
    @Path("pipelineConfigId") pipelineConfigId: String,
    @Body trigger: Map<String, Any?>
  ): TaskRefResponse

  @GET("/tasks/{id}")
  suspend fun getTask(@Path("id") id: String): ExecutionDetailResponse

  @GET("/pipelines/{id}")
  suspend fun getPipelineExecution(@Path("id") id: String): ExecutionDetailResponse

  @GET("/executions/correlated/{correlationId}")
  suspend fun getCorrelatedExecutions(@Path("correlationId") correlationId: String): List<String>
}

data class TaskRefResponse(
  val ref: String
) {
  val taskId by lazy { ref.substringAfterLast("/") }
}

data class ExecutionDetailResponse(
  val id: String,
  val name: String,
  val application: String,
  val buildTime: Instant,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: OrcaExecutionStatus
)
