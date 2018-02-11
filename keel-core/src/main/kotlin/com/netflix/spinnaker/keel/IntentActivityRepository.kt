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

import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.state.FieldState

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

/**
 * @param intentId The ID of the intent that is being evaluated.
 * @param changeType The type of change that took place this cycle.
 * @param orchestrations A resultant (if any) list of orchestration IDs from the intent.
 * @param messages Human-friendly messages about the change.
 * @param diff The diff between current and desired state.
 * @param actor Who (or what) initiated the operation.
 * @param timestampMillis The timestamp in millis the record was created.
 */
data class IntentConvergenceRecord(
  val intentId: String,
  val changeType: ChangeType,
  val orchestrations: List<String>?,
  val messages: List<String>?,
  val diff: Set<FieldState>,
  val actor: String,
  val timestampMillis: Long
)

interface IntentActivityRepository {

  fun addOrchestration(intentId: String, orchestrationId: String)

  fun addOrchestrations(intentId: String, orchestrations: List<String>)

  fun getHistory(intentId: String): List<String>

  fun logConvergence(intentConvergenceRecord: IntentConvergenceRecord)

  fun getLog(intentId: String): List<IntentConvergenceRecord>

  /**
   * Permalink to a specific log message, identified by timestampMillis
   * @param intentId The ID of the intent that is being evaluated.
   * @param timestampMillis The timestamp of the log message,
   *  used as the unique identifier of the message, in milliseconds.
   */
  fun getLogEntry(intentId: String, timestampMillis: Long): IntentConvergenceRecord?

  /**
   * If orchestrationId is passed in as a link to the task in orca, strip the leading path
   * off so that we're storing the actual orchestrationId
   */
  fun parseOrchestrationId(orchestrationId: String) = orchestrationId.removePrefix("/tasks/")
}
