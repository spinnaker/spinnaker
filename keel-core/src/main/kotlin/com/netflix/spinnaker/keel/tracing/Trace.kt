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
package com.netflix.spinnaker.keel.tracing

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec

/**
 * @param activityId An optional link to the activity associated with this trace
 * @param startingState An arbitrary model of state as it existed at the beginning of an operation.
 * @param intent The desired intent that caused the operation.
 * @param createTs A timestamp of when this operation occurred.
 */
data class Trace(
  val startingState: Map<String, Any>,
  val intent: Intent<IntentSpec>,
  val createTs: Long? = null,
  val activityId: String? = null
)
