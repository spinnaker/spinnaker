/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.event

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

/**
 * The base event class for the event sourcing library.
 *
 * @property id A unique ID for the event. This value is for tracing, rather than loading events
 * @property aggregateType The type of aggregate the event is for
 * @property aggregateId The id of the aggregate the event is for
 * @property metadata Associated metadata about the event; not actually part of the "event proper"
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "spinEventType"
)
abstract class SpinnakerEvent {
  val id = UUID.randomUUID().toString()

  lateinit var aggregateType: String
  lateinit var aggregateId: String
  lateinit var metadata: EventMetadata
}
