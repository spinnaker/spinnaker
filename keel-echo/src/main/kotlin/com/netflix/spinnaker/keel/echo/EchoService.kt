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
package com.netflix.spinnaker.keel.echo

import com.fasterxml.jackson.annotation.JsonInclude
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.POST

interface EchoService {

//  @Headers("Content-type: application/json")
  @POST("/notifications")
  fun create(@Body notification: Notification): Response

  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class Notification(
    val notificationType: Type,
    val to: Collection<String>,
    val cc: Collection<String> = listOf(),
    val templateGroup: String,
    val severity: NotificationSeverity,
    val source: Source,
    val additionalContext: Map<String, Any?> = mapOf()
  ) {

    data class Source(val application: String)

    enum class Type {
      SLACK,
      HIPCHAT,
      EMAIL,
      SMS,
      PAGER_DUTY
    }
  }
}
