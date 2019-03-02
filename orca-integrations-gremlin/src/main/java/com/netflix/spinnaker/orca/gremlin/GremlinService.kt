package com.netflix.spinnaker.orca.gremlin

import retrofit.http.*;

interface GremlinService {
  @POST("/attacks/new")
  @Headers(
    "Content-Type: application/json",
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun create(
    @Header("Authorization") authHeader: String,
    @Body attackParameters: AttackParameters
  ): String

  @GET("/executions")
  @Headers(
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun getStatus(
    @Header("Authorization") authHeader: String,
    @Query("taskId") attackGuid: String
  ): List<AttackStatus>

  @DELETE("/attacks/{attackGuid}")
  @Headers(
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun haltAttack(
    @Header("Authorization") authHeader: String,
    @Path("attackGuid") attackGuid: String
  ): Void
}

data class AttackParameters(
  val command: Map<String, Any>,
  val target: Map<String, Any>
)

data class AttackStatus(
  val guid: String,
  val stage: String,
  val stageLifecycle: String,
  val endTime: String?,
  val output: String?
)
