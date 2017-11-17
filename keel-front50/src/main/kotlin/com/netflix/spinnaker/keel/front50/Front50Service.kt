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
package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.front50.model.Application
import retrofit.client.Response
import retrofit.http.*

interface Front50Service {

  @GET("/intents")
  fun getIntents(): List<Intent<IntentSpec>>

  @GET("/intents")
  fun getIntentsByStatus(@Query("status") status: List<IntentStatus>?): List<Intent<IntentSpec>>

  @GET("/intents/{id}")
  fun getIntent(@Path("id") id: String): Intent<IntentSpec>

  @POST("/intents")
  fun upsertIntent(@Body intent: Intent<IntentSpec>): Intent<IntentSpec>

  @DELETE("/intents/{id}")
  fun deleteIntent(@Path("id") id: String): Response

  @GET("/v2/applications/{applicationName}")
  fun getApplication(@Path("applicationName") applicationName: String): Application
}
