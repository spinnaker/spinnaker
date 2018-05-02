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
package com.netflix.spinnaker.orca.qos.bufferpolicy

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.qos.BufferAction.BUFFER
import com.netflix.spinnaker.orca.qos.BufferAction.ENQUEUE
import com.netflix.spinnaker.orca.qos.BufferPolicy
import com.netflix.spinnaker.orca.qos.BufferResult
import org.springframework.stereotype.Component

/**
 * Deck-initiated orchestrations should always be enqueued. This buffer policy cannot be disabled.
 */
@Component
class EnqueueDeckOrchestrationsBufferPolicy : BufferPolicy {
  override fun apply(execution: Execution): BufferResult {
    if (execution.type == ORCHESTRATION && execution.origin == "deck") {
      return BufferResult(
        action = ENQUEUE,
        force = true,
        reason = "Deck-initiated orchestrations are always enqueued"
      )
    }
    return BufferResult(
      action = BUFFER,
      force = false,
      reason = "Execution is not a deck-initiated orchestration"
    )
  }

  override fun getOrder(): Int = Int.MAX_VALUE
}
