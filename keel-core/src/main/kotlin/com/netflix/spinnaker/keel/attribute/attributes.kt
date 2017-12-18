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
package com.netflix.spinnaker.keel.attribute

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription
import com.netflix.spinnaker.keel.IntentPriority
import javax.validation.constraints.Max
import javax.validation.constraints.Min

/**
 * An Attribute is a strictly typed key/value pair. They're attached as a collection of metadata on Intents and used
 * by Filters, Policies and event handlers for performing direct or indirect actions on Intents.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Attribute<out T>
@JsonCreator constructor(
  val kind: String,
  val value: T
)

/**
 * Defines the namespace-specific priority of an intent.
*/
@JsonTypeName("Priority")
class PriorityAttribute(value: IntentPriority) : Attribute<IntentPriority>("Priority", value)

/**
 * Defines whether or not an Intent's desired state should be getting actively converged. Release valve.
 */
@JsonTypeName("Enabled")
class EnabledAttribute(value: Boolean) : Attribute<Boolean>("Enabled", value)

/**
 * Defines at what times during the time of day & weekly schedule an Intent should be a candidate for being converged.
 */
@JsonTypeName("ExecutionWindow")
@JsonSchemaDescription("Defines when an intent will be allowed to converge on state changes")
class ExecutionWindowAttribute(value: ExecutionWindow) : Attribute<ExecutionWindow>("ExecutionWindow", value) {
  override fun toString(): String {
    return "ExecutionWindowAttribute(days=${value.days}, timeWindows=${value.timeWindows})"
  }
}

data class ExecutionWindow(
  val days: List<Int>,
  val timeWindows: List<TimeWindow>
)

data class TimeWindow(
  @Min(0) @Max(23) val startHour: Int,
  @Min(0) @Max(60) val startMin: Int,
  @Min(0) @Max(23) val endHour: Int,
  @Min(0) @Max(60) val endMin: Int
)
