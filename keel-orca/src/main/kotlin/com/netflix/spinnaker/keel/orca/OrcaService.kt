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
import kotlinx.coroutines.Deferred
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import java.time.Instant

// TODO Origin needs to be set on executions
// origin is used for dynamic routing by orca to different clouddriver instances
interface OrcaService {

  @POST("/ops")
  @Headers("Content-Type: application/context+json")
  fun orchestrate(@Body request: OrchestrationRequest): Deferred<TaskRefResponse>

  @GET("/tasks/{id}")
  fun getTask(@Path("id") id: String): Deferred<TaskDetailResponse>
}

data class TaskRefResponse(
  val ref: String
) {
  val taskId by lazy { ref.substringAfterLast("/") }
}

data class TaskDetailResponse(
  val id: String,
  val name: String,
  val application: String,
  val buildTime: Instant,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: OrcaExecutionStatus
)
