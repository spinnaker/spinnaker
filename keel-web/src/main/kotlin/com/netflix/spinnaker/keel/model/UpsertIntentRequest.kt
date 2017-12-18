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
package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec

/**
 * @param reason User-supplied reason of why the intent is changing.
 * @param intents A list of all intents that are being submitted with this change unit.
 * @param dryRun Whether or not to describe expected actions if applied, but wont' apply any changes.
 */
data class UpsertIntentRequest(
  val reason: String?,
  val intents: List<Intent<IntentSpec>>,
  val dryRun: Boolean = false
)
