/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.keel.annotation.Computed

data class PipelineConfig(
  val application: String,
  val name: String,
  val parameterConfig: List<Map<String, Any?>>?,
  val triggers: List<Map<String, Any?>>?,
  val notifications: List<Map<String, Any?>>?,
  val stages: List<Map<String, Any?>>,
  val spelEvaluator: String?,
  val executionEngine: String?,
  val limitConcurrent: Boolean?,
  val keepWaitingPipelines: Boolean?,
  @Computed val id: String?,
  @Computed val index: Int?,
  @Computed val stageCounter: Int?,
  @Computed val lastModifiedBy: String?,
  @Computed val updateTs: String?
) {

  private val details: MutableMap<String, Any?> = mutableMapOf()

  @JsonAnySetter
  fun set(name: String, value: Any?) {
    details[name] = value
  }

  @JsonAnyGetter
  fun details() = details
}
