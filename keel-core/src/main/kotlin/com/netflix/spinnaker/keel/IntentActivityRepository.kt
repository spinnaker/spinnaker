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
package com.netflix.spinnaker.keel

/**
 * TODO rz - refactor IntentActivityRepository to use this instead
 *
 * @param intentId The ID of the intent that is being evaluated.
 * @param orchestrations A resultant (if any) list of orchestration IDs from the intent.
 * @param reason A human-friendly description of why this operation occurred.
 * @param actor Who (or what) initiated the operation.
 */
data class IntentActivity(
  val intentId: String,
  val orchestrations: List<String>,
  val reason: String,
  val actor: String
)

interface IntentActivityRepository {

  fun addOrchestration(intentId: String, orchestrationId: String)

  fun addOrchestrations(intentId: String, orchestrations: List<String>)

  fun getCurrent(intentId: String): List<String>

  @Deprecated("Tracking downstream orchestrations is not currently planned for support")
  fun upsertCurrent(intentId: String, orchestrations: List<String>)

  @Deprecated("Tracking downstream orchestrations is not currently planned for support")
  fun upsertCurrent(intentId: String, orchestration: String)

  @Deprecated("Tracking downstream orchestrations is not currently planned for support")
  fun removeCurrent(intentId: String, orchestrationId: String)

  @Deprecated("Tracking downstream orchestrations is not currently planned for support")
  fun removeCurrent(intentId: String)

  fun getHistory(intentId: String): List<String>

  /**
   * If orchestrationId is passed in as a link to the task in orca, strip the leading path
   * off so that we're storing the actual orchestrationId
   */
  fun parseOrchestrationId(orchestrationId: String) = orchestrationId.removePrefix("/tasks/")
}
