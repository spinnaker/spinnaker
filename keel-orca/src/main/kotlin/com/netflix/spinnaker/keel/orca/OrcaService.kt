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
import retrofit.http.Body
import retrofit.http.Headers
import retrofit.http.POST

// TODO Origin needs to be set on executions
interface OrcaService {

  @POST("/ops")
  @Headers("Content-Type: application/context+json")
  fun orchestrate(@Body request: OrchestrationRequest): TaskRefResponse
}

data class TaskRefResponse(
  val ref: String
)
