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

import java.time.Instant

/**
 * Metadata for a [SpinnakerEvent].
 *
 * @param sequence Auto-incrementing number for event ordering
 * @param originatingVersion The aggregate version that originated this event
 * @param timestamp The time at which the event was created
 * @param serviceVersion The version of the service (clouddriver) that created the event
 * @param source Where/what generated the event
 */
data class EventMetadata(
  val sequence: Long,
  val originatingVersion: Long,
  val timestamp: Instant = Instant.now(),
  val serviceVersion: String = "unknown",
  val source: String = "unknown"
)
