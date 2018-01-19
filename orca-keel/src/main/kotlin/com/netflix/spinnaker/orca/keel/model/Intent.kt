/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.keel.model

data class Intent(
  val kind: String,
  val schema: String,
  val spec: Map<String, Any?>,
  val status: String = "ACTIVE",
  val labels: Map<String, String> = mapOf(),
  val attributes: List<Any> = listOf(),
  val policies: List<Any> = listOf(),
  val id: String?,
  val namespace: String?,
  val idOverride: String?
)

data class UpsertIntentRequest(
  val intents: List<Intent>,
  val dryRun: Boolean
)

data class UpsertIntentResponse(
  val intentId: String,
  val intentStatus: String
)

data class UpsertIntentDryRunResponse(
  val summary: Map<String, Any?>
)
