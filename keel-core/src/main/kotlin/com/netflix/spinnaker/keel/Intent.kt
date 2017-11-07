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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.jonpeterson.jackson.module.versioning.JsonSerializeToVersion
import com.netflix.spectator.api.BasicTag

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Intent<out S : IntentSpec>
@JsonCreator constructor(
  @JsonSerializeToVersion(defaultToSource = true) val schema: String,
  val kind: String,
  val spec: S,
  val status: IntentStatus = IntentStatus.ACTIVE,
  val policies: List<Policy> = listOf()
) {

  abstract fun getId(): String

  @JsonIgnore
  fun getMetricTags() = listOf(BasicTag("kind", kind), BasicTag("schema", schema))
}
