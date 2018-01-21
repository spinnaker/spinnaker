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
package com.netflix.spinnaker.keel.scheduler

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.q.Message

// ScheduleConvergence is a singleton message, the consumer finding all active
// intents and scheduling workers to consume individual convergence tasks. The
// consumer is responsible for rescheduling this message.
@JsonTypeName(value = "scheduleConvergence")
class ScheduleConvergence : Message()

@JsonTypeName(value = "convergeIntent")
data class ConvergeIntent(
  val intent: Intent<IntentSpec>,
  // The timestamp of which the intent data should be considered stale. If
  // stale, the worker should just go to the datastore to refresh state
  // rather than erroring out.
  val stalenessTtl: Long,
  // ... Unless the timeout ttl is elapsed.
  val timeoutTtl: Long
) : Message()

@JsonTypeName(value = "monitorOrchestrations")
data class MonitorOrchestrations(
  val intentId: String,
  val kind: String
) : Message()
