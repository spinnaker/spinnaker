/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.time.Duration

open class PeeringMetrics(
  peeredId: String,
  private val registry: MeterRegistry
) {

  private val peerIdTags = Tags.of("peerId", peeredId)

  open fun recordOverallLag(block: () -> Unit) {
    registry
      .timer("pollers.peering.lag", peerIdTags.and("executionType", "OVER_ALL"))
      .record(Runnable { block() })
  }

  open fun recordLag(executionType: ExecutionType, duration: Duration) {
    registry
      .timer("pollers.peering.lag", peerIdTags.tag(executionType))
      .record(duration)
  }

  open fun incrementNumPeered(executionType: ExecutionType, state: ExecutionState, count: Int) {
    registry
      .counter("pollers.peering.numPeered", peerIdTags.tag(executionType, state))
      .increment(count.toDouble())
  }

  open fun incrementNumDeleted(executionType: ExecutionType, count: Int) {
    registry
      .counter("pollers.peering.numDeleted", peerIdTags.tag(executionType))
      .increment(count.toDouble())
  }

  open fun incrementNumErrors(executionType: ExecutionType) {
    registry
      .counter("pollers.peering.numErrors", peerIdTags.tag(executionType))
      .increment()
  }

  open fun incrementNumStagesDeleted(executionType: ExecutionType, count: Int) {
    registry
      .counter("pollers.peering.numStagesDeleted", peerIdTags.tag(executionType))
      .increment(count.toDouble())
  }

  open fun incrementCustomPeererError(peererName: String, exception: Exception) {
    registry
      .counter(
        "pollers.peering.customPeerer.numErrors",
        peerIdTags.and("peerer", peererName, "exception", exception.javaClass.simpleName)
      )
      .increment()
  }
}

internal fun Tags.tag(executionType: ExecutionType): Tags {
  return this.and("executionType", executionType.toString())
}

internal fun Tags.tag(executionType: ExecutionType, state: ExecutionState): Tags {
  return this
    .and("executionType", executionType.toString())
    .and("state", state.toString())
}

enum class ExecutionState {
  ACTIVE,
  COMPLETED,
}
