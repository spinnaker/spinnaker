/*
 * Copyright 2018 Netflix, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.orca.ExecutionStatus
import retrofit.http.*
import java.time.Duration
import java.time.Instant

interface KayentaService {

  @POST("/canary/{canaryConfigId}")
  fun create(
    @Path("canaryConfigId") canaryConfigId: String,
    @Query("application") application: String,
    @Query("parentPipelineExecutionId") parentPipelineExecutionId: String,
    @Query("metricsAccountName") metricsAccountName: String?,
    @Query("configurationAccountName") configurationAccountName: String?,
    @Query("storageAccountName") storageAccountName: String?,
    @Body canaryExecutionRequest: CanaryExecutionRequest
  ): Map<*, *>

  @GET("/canary/{canaryExecutionId}")
  fun getCanaryResults(
    @Query("storageAccountName") storageAccountName: String?,
    @Path("canaryExecutionId") canaryExecutionId: String
  ): CanaryResults

  @PUT("/pipelines/{executionId}/cancel")
  fun cancelPipelineExecution(
    @Path("executionId") executionId: String,
    @Body ignored: String
  ): Map<*, *>

  @GET("/credentials")
  fun getCredentials(): List<KayentaCredential>
}

data class CanaryExecutionRequest(
  val scopes: Map<String, CanaryScopes> = emptyMap(),
  val thresholds: Thresholds
)

data class CanaryScopes(
  val controlScope: CanaryScope,
  val experimentScope: CanaryScope
)

data class CanaryScope(
  val scope: String?,
  val location: String?,
  val start: Instant,
  val end: Instant,
  val step: Long = 60, // TODO: would be nice to use a Duration
  val extendedScopeParams: Map<String, String> = emptyMap()
)

data class Thresholds(
  val pass: Int,
  val marginal: Int
)

data class CanaryResults(
  val complete: Boolean,
  val status: String,
  val result: CanaryResult?,
  val buildTimeIso: Instant?,
  val startTimeIso: Instant?,
  val endTimeIso: Instant?,
  val storageAccountName: String?,
  val application: String,
  val canaryExecutionRequest: CanaryExecutionRequest?,
  val exception: Map<String, Any>?
) {
  @JsonIgnore
  val executionStatus = ExecutionStatus.valueOf(status.toUpperCase())
}

data class CanaryResult(
  val judgeResult: JudgeResult,
  val canaryDuration: Duration
)

data class JudgeResult(
  val score: JudgeScore,
  val results: Array<JudgeResultEntry>
)

data class JudgeScore(
  val score: Int,
  val classification: String,
  val classificationReason: String
)

data class JudgeResultEntry(
  val controlMetadata: ControlMetadata
)

data class ControlMetadata(
  val stats: ControlMetadataStats
)

data class ControlMetadataStats(
  val count: Int
)

data class KayentaCredential(
  val name: String,
  val type: String
)
