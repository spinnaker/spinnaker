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
package com.netflix.spinnaker.orca.qos

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.transientconfig.TransientConfigService
import com.netflix.spinnaker.orca.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.annotations.Sync
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.BufferAction.BUFFER
import com.netflix.spinnaker.orca.qos.BufferAction.ENQUEUE
import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Determines if an execution should be buffered.
 */
@Component
class ExecutionBufferActuator(
  private val bufferStateSupplier: BufferStateSupplier,
  private val transientConfigService: TransientConfigService,
  private val registry: Registry,
  policies: List<BufferPolicy>
) {

  private val log = LoggerFactory.getLogger(ExecutionBufferActuator::class.java)

  private val orderedPolicies = policies.sortedByDescending { it.order }.toList()

  private val bufferedId = registry.createId("qos.executionsBuffered")
  private val elapsedTimeId = registry.createId("qos.promoter.elapsedTime")

  @Sync
  @EventListener(BeforeInitialExecutionPersist::class)
  fun beforeInitialPersist(event: BeforeInitialExecutionPersist) {
    if (bufferStateSupplier.get() == ACTIVE) {
      val execution = event.execution
      withActionDecision(execution) {
        when (it.action) {
          BUFFER -> {
            if (transientConfigService.isEnabled("qos.learningMode", true)) {
              log.debug("Learning mode: Would have buffered execution: {}, reason ${it.reason}", value("executionId", execution.id))
            } else {
              log.warn("Buffering execution: {}, reason: ${it.reason}", value("executionId", execution.id))
              execution.status = BUFFERED
              registry.counter(bufferedId).increment()
            }
          }
          ENQUEUE -> {
            log.debug("Enqueuing execution: {}, reason: ${it.reason}", value("executionId", execution.id))
          }
        }
      }
    }
  }

  fun withActionDecision(execution: Execution, fn: (BufferResult) -> Unit) {
    registry.timer(elapsedTimeId).record {
      orderedPolicies
        .map { it.apply(execution) }
        .let { bufferResults ->
          if (bufferResults.isEmpty()) {
            return@let null
          }

          val forcedDecision = bufferResults.firstOrNull { it.force }
          if (forcedDecision != null) {
            return@let forcedDecision
          }

          // Require all results to call for enqueuing the execution, otherwise buffer.
          val enqueue = bufferResults.all { it.action == ENQUEUE }
          val reasons = bufferResults.joinToString(",") { it.reason }

          return@let BufferResult(
            action = if (enqueue) ENQUEUE else BUFFER,
            force = false,
            reason = reasons
          )
        }
        ?.run { fn.invoke(this) }
    }
  }
}
