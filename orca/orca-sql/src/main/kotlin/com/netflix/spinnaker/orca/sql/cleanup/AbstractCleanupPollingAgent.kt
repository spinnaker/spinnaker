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

package com.netflix.spinnaker.orca.sql.cleanup

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractCleanupPollingAgent(
  clusterLock: NotificationClusterLock,
  private val pollingIntervalMs: Long,
  val registry: MeterRegistry
) : AbstractPollingNotificationAgent(clusterLock) {

  val log: Logger = LoggerFactory.getLogger(javaClass)
  val completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }

  val deletedMetricName: String = "pollers.$notificationType.deleted"
  val errorsCounter: Counter = registry.counter("pollers.$notificationType.errors")
  val invocationTimer: LongTaskTimer = LongTaskTimer.builder("pollers.$notificationType.timing").register(registry)

  abstract fun performCleanup()

  override fun tick() {
    val sample = invocationTimer.start()
    val startTime = System.currentTimeMillis()

    try {
      log.info("Agent $notificationType started")
      performCleanup()
    } catch (e: Exception) {
      log.error("Agent $notificationType failed to perform cleanup", e)
    } finally {
      log.info("Agent $notificationType completed in ${System.currentTimeMillis() - startTime}ms")
      sample.stop()
    }
  }

  override fun getPollingInterval(): Long {
    return pollingIntervalMs
  }

  override fun getNotificationType(): String {
    return javaClass.simpleName
  }
}
