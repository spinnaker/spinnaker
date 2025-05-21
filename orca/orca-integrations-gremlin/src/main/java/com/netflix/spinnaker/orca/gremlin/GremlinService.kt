package com.netflix.spinnaker.orca.gremlin

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GremlinService {
  @POST("attacks/new")
  @Headers(
    "Content-Type: application/json",
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun create(
    @Header("Authorization") authHeader: String,
    @Body attackParameters: AttackParameters
  ): Call<String>

  @GET("executions")
  @Headers(
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun getStatus(
    @Header("Authorization") authHeader: String,
    @Query("taskId") attackGuid: String
  ): Call<List<AttackStatus>>

  @DELETE("attacks/{attackGuid}")
  @Headers(
    "X-Gremlin-Agent: spinnaker/0.1.0"
  )
  fun haltAttack(
    @Header("Authorization") authHeader: String,
    @Path("attackGuid") attackGuid: String
  ): Call<Void>
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
