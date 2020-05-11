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

import com.fasterxml.jackson.annotation.JsonAlias
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import java.time.Instant
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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
    @Body trigger: Map<String, Any>
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

  @GET("/executions/correlated/{correlationId}")
  suspend fun getCorrelatedExecutions(
    @Path("correlationId") correlationId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<String>
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
  val status: OrcaExecutionStatus,
  val execution: OrcaExecutionStages = OrcaExecutionStages(emptyList()),
  val stages: List<OrcaExecutionStage>? = emptyList() // for pipelines, stages are not encapsulated in `execution`
)

typealias OrcaExecutionStage = Map<String, Any>

data class OrcaExecutionStages(
  val stages: List<OrcaExecutionStage>?
)

data class GeneralErrorsDetails(
  val stackTrace: String?,
  val responseBody: String?,
  val kind: String?,
  val error: String,
  val errors: List<String>
)

data class OrcaException(
  val exceptionType: String,
  val shouldRetry: Boolean,
  val details: GeneralErrorsDetails
)

data class ClouddriverException(
  val cause: String?,
  val message: String,
  val type: String,
  val operation: String?
)

data class OrcaContext(
  // fetching only orca general and kato exceptions for now
  val exception: OrcaException?,
  @JsonAlias("kato.tasks")
  val clouddriverException: List<Map<String, Any>>?
)
