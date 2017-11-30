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

interface IntentActivityRepository {

  fun addOrchestration(intentId: String, orchestrationId: String)

  fun addOrchestrations(intentId: String, orchestrations: List<String>)

  fun getCurrent(intentId: String): List<String>

  fun upsertCurrent(intentId: String, orchestrations: List<String>)

  fun upsertCurrent(intentId: String, orchestration: String)

  fun removeCurrent(intentId: String, orchestrationId: String)

  fun removeCurrent(intentId: String)

  fun getHistory(intentId: String): List<String>

  /**
   * If orchestrationId is passed in as a link to the task in orca, strip the leading path
   * off so that we're storing the actual orchestrationId
   */
  fun parseOrchestrationId(orchestrationId: String) = orchestrationId.removePrefix("/tasks/")
}
