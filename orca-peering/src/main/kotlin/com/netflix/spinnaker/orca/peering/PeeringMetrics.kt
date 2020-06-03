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

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import java.time.Duration

open class PeeringMetrics(
  peeredId: String,
  private val registry: Registry
) {

  private val peeringLagTimerId = registry.createId("pollers.peering.lag").withTag("peerId", peeredId)
  private val peeringNumPeeredId = registry.createId("pollers.peering.numPeered").withTag("peerId", peeredId)
  private val peeringNumDeletedId = registry.createId("pollers.peering.numDeleted").withTag("peerId", peeredId)
  private val peeringNumStagesDeletedId = registry.createId("pollers.peering.numStagesDeleted").withTag("peerId", peeredId)
  private val peeringNumErrorsId = registry.createId("pollers.peering.numErrors").withTag("peerId", peeredId)
  private val peeringCustomPeererNumErrorsId = registry.createId("pollers.peering.customPeerer.numErrors").withTag("peerId", peeredId)

  open fun recordOverallLag(block: () -> Unit) {
    registry
      .timer(peeringLagTimerId.withTag("executionType", "OVER_ALL"))
      .record {
        block()
      }
  }

  open fun recordLag(executionType: ExecutionType, duration: Duration) {
    registry
      .timer(peeringLagTimerId.tag(executionType))
      .record(duration)
  }

  open fun incrementNumPeered(executionType: ExecutionType, state: ExecutionState, count: Int) {
    registry
      .counter(peeringNumPeeredId.tag(executionType, state))
      .increment(count.toLong())
  }

  open fun incrementNumDeleted(executionType: ExecutionType, count: Int) {
    registry
      .counter(peeringNumDeletedId.tag(executionType))
      .increment(count.toLong())
  }

  open fun incrementNumErrors(executionType: ExecutionType) {
    registry
      .counter(peeringNumErrorsId.tag(executionType))
      .increment()
  }

  open fun incrementNumStagesDeleted(executionType: ExecutionType, count: Int) {
    registry
      .counter(peeringNumStagesDeletedId.tag(executionType))
      .increment(count.toLong())
  }

  open fun incrementCustomPeererError(peererName: String, exception: Exception) {
    registry
      .counter(peeringCustomPeererNumErrorsId.withTags("peerer", peererName, "exception", exception.javaClass.simpleName))
      .increment()
  }
}

internal fun Id.tag(executionType: ExecutionType): Id {
  return this
    .withTag("executionType", executionType.toString())
}

internal fun Id.tag(executionType: ExecutionType, state: ExecutionState): Id {
  return this
    .withTag("executionType", executionType.toString())
    .withTag("state", state.toString())
}

enum class ExecutionState {
  ACTIVE,
  COMPLETED,
}
