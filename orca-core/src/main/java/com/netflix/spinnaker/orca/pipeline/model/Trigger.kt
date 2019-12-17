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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.pipeline.model.support.TriggerDeserializer

@JsonDeserialize(using = TriggerDeserializer::class)
interface Trigger {
  val type: String
  val correlationId: String?
  val user: String?
  val parameters: Map<String, Any>
  val artifacts: List<Artifact>
  val notifications: List<Map<String, Any>>
  @get:JsonProperty("rebake")
  var isRebake: Boolean
  @get:JsonProperty("dryRun")
  var isDryRun: Boolean
  @get:JsonProperty("strategy")
  var isStrategy: Boolean
  var resolvedExpectedArtifacts: List<ExpectedArtifact>
  @set:JsonAnySetter
  @get:JsonAnyGetter
  var other: Map<String, Any>
}
