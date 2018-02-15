package com.netflix.spinnaker.orca.kayenta

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
  ): Map<*, *>

  @PUT("/pipelines/{executionId}/cancel")
  fun cancelPipelineExecution(
    @Path("executionId") executionId: String,
    @Body ignored: String
  ): Map<*, *>
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
  val scope: String,
  val region: String?,
  val start: Instant,
  val end: Instant,
  val step: Duration = Duration.ofSeconds(60),
  val extendedScopeParams: Map<String, String> = emptyMap()
)

data class Thresholds(
  val pass: Int,
  val marginal: Int
)
