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
import com.netflix.spinnaker.orca.pipeline.model.Execution

open class PeeringMetrics(
  peeredId: String,
  private val registry: Registry
) {

  private val peeringLagTimerId = registry.createId("pollers.peering.lag").withTag("peerId", peeredId)
  private val peeringNumPeeredId = registry.createId("pollers.peering.numPeered").withTag("peerId", peeredId)
  private val peeringNumDeletedId = registry.createId("pollers.peering.numDeleted").withTag("peerId", peeredId)
  private val peeringNumStagesDeletedId = registry.createId("pollers.peering.numStagesDeleted").withTag("peerId", peeredId)
  private val peeringNumErrorsId = registry.createId("pollers.peering.numErrors").withTag("peerId", peeredId)

  open fun recordLag(executionType: Execution.ExecutionType, block: () -> Unit) {
    registry
      .timer(peeringLagTimerId.tag(executionType))
      .record {
        block()
      }
  }

  open fun incrementNumPeered(executionType: Execution.ExecutionType, state: ExecutionState, count: Int) {
    registry
      .counter(peeringNumPeeredId.tag(executionType, state))
      .increment(count.toLong())
  }

  open fun incrementNumDeleted(executionType: Execution.ExecutionType, count: Int) {
    registry
      .counter(peeringNumDeletedId.tag(executionType))
      .increment(count.toLong())
  }

  open fun incrementNumErrors(executionType: Execution.ExecutionType) {
    registry
      .counter(peeringNumErrorsId.tag(executionType))
      .increment()
  }

  open fun incrementNumStagesDeleted(executionType: Execution.ExecutionType, count: Int) {
    registry
      .counter(peeringNumStagesDeletedId.tag(executionType))
      .increment(count.toLong())
  }
}

internal fun Id.tag(executionType: Execution.ExecutionType): Id {
  return this
    .withTag("executionType", executionType.toString())
}

internal fun Id.tag(executionType: Execution.ExecutionType, state: ExecutionState): Id {
  return this
    .withTag("executionType", executionType.toString())
    .withTag("state", state.toString())
}

enum class ExecutionState {
  ACTIVE,
  COMPLETED,
}
