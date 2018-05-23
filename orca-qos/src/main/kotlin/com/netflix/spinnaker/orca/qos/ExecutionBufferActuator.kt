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
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigSerivce
import com.netflix.spinnaker.orca.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.annotations.Sync
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.BufferAction.BUFFER
import com.netflix.spinnaker.orca.qos.BufferAction.ENQUEUE
import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import com.netflix.spinnaker.orca.qos.bufferstate.BufferStateSupplierProvider
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Determines if an execution should be buffered.
 */
@Component
class ExecutionBufferActuator(
  private val bufferStateSupplierProvider: BufferStateSupplierProvider,
  private val configService: DynamicConfigSerivce,
  private val registry: Registry,
  policies: List<BufferPolicy>
) {

  private val log = LoggerFactory.getLogger(ExecutionBufferActuator::class.java)

  private val orderedPolicies = policies.sortedByDescending { it.order }.toList()

  private val bufferedId = registry.createId("qos.executionsBuffered")
  private val enqueuedId = registry.createId("qos.executionsEnqueued")
  private val elapsedTimeId = registry.createId("qos.actuator.elapsedTime")

  @Sync
  @EventListener(BeforeInitialExecutionPersist::class)
  fun beforeInitialPersist(event: BeforeInitialExecutionPersist) {
    if (!configService.isEnabled("qos", false)) {
      return
    }

    val bufferStateSupplier = bufferStateSupplierProvider.provide()

    val supplierName = bufferStateSupplier.javaClass.simpleName
    if (bufferStateSupplier.get() == ACTIVE) {
      val execution = event.execution
      withActionDecision(execution) {
        when (it.action) {
          BUFFER -> {
            if (configService.isEnabled("qos.learningMode", true)) {
              log.debug("Learning mode: Would have buffered execution {} (using $supplierName), reason: ${it.reason}", value("executionId", execution.id))
              registry.counter(bufferedId.withTag("learning", "true")).increment()
            } else {
              log.warn("Buffering execution {} (using $supplierName), reason: ${it.reason}", value("executionId", execution.id))
              registry.counter(bufferedId.withTag("learning", "false")).increment()
              execution.status = BUFFERED
            }
          }
          ENQUEUE -> {
            log.debug("Enqueuing execution {} (using $supplierName), reason: ${it.reason}", value("executionId", execution.id))
            registry.counter(enqueuedId).increment()
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
          val reasons = bufferResults.joinToString(", ") { it.reason }

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
