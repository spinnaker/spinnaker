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

import com.netflix.spinnaker.kork.artifacts.model.Artifact

data class OrchestrationRequest(
  val name: String,
  val application: String,
  val description: String,
  val job: List<Job>,
  val trigger: OrchestrationTrigger
)

class Job(type: String, m: Map<String, Any?>) : HashMap<String, Any?>(m + mapOf("type" to type, "user" to "Spinnaker"))

data class OrchestrationTrigger(
  val correlationId: String,
  val notifications: List<OrcaNotification>,
  val type: String = "keel",
  val user: String = "keel",
  val artifacts: List<Artifact> = emptyList()
)
