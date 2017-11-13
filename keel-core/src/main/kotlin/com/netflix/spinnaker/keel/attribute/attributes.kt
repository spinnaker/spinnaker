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
import com.netflix.spinnaker.keel.IntentPriority

/**
 * An Attribute is a strictly typed key/value pair. They're attached as a collection of metadata on Intents and used
 * by Filters, Policies and EventHandlers for performing direct or indirect actions on Intents.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Attribute<out T>
@JsonCreator constructor(
  val kind: String,
  val value: T
)

@JsonTypeName("Priority")
class PriorityAttribute(value: IntentPriority) : Attribute<IntentPriority>("Priority", value)

@JsonTypeName("Enabled")
class EnabledAttribute(value: Boolean) : Attribute<Boolean>("Enabled", value)
