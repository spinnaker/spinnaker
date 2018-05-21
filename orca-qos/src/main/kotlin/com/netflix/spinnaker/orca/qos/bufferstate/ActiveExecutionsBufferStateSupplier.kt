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
package com.netflix.spinnaker.orca.qos.bufferstate

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigSerivce
import com.netflix.spinnaker.orca.qos.BufferState
import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import com.netflix.spinnaker.orca.qos.BufferState.INACTIVE
import com.netflix.spinnaker.orca.qos.BufferStateSupplier
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Supplies the buffering state based on a threshold of system-wide active executions.
 *
 * As an immediate implementation, this supplier depends on [RedisActiveExecutionMonitor] to update the registry with
 * system-wide active executions. The polling interval of that monitor will determine the resolution of the buffering
 * state. By default, this is 60 seconds.
 */
@Component
class ActiveExecutionsBufferStateSupplier(
  private val configService: DynamicConfigSerivce,
  private val registry: Registry
) : BufferStateSupplier {

  private val log = LoggerFactory.getLogger(ActiveExecutionsBufferStateSupplier::class.java)

  private var state: BufferState = INACTIVE

  private val bufferingId = registry.createId("qos.buffering")

  @Scheduled(fixedDelayString = "\${pollers.qos.updateStateIntervalMs:5000}")
  private fun updateCurrentState() {
    if (!enabled()) {
      state = INACTIVE
      return
    }

    val activeExecutions = registry.gauges()
      .filter { it.id().name() == "executions.active" }
      .map { it.value() }
      .reduce { p: Double, o: Double -> o + p }
      .get().toInt()

    val threshold = getThreshold()
    state = if (activeExecutions > threshold) {
      if (state == INACTIVE) {
        log.warn("Enabling buffering: System active executions over threshold ($activeExecutions/$threshold)")
        registry.gauge(bufferingId).set(1.0)
      }
      ACTIVE
    } else {
      if (state == ACTIVE) {
        log.warn("Disabling buffering: System active executions below threshold ($activeExecutions/$threshold)")
        registry.gauge(bufferingId).set(0.0)
      }
      INACTIVE
    }
  }

  override fun get() = state

  override fun enabled() =
    configService.getConfig(String::class.java, "qos.bufferingState.supplier", "") == "activeExecutions"

  private fun getThreshold() =
    configService.getConfig(Int::class.java, "qos.bufferingState.activeExecutions.threshold", 100)
}
