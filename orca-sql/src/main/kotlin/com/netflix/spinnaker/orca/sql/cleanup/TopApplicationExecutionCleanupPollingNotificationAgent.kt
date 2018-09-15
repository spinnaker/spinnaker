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

package com.netflix.spinnaker.orca.sql.cleanup

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.LongTaskTimer
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.sql.pipeline.persistence.transactional
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger


@Component
@ConditionalOnExpression("\${pollers.topApplicationExecutionCleanup.enabled:false} && !\${executionRepository.redis.enabled:false}")
class TopApplicationExecutionCleanupPollingNotificationAgent(
  clusterLock: NotificationClusterLock,
  private val jooq: DSLContext,
  private val registry: Registry,
  private val retrySupport: RetrySupport,
  @Value("\${pollers.topApplicationExecutionCleanup.intervalMs:3600000}") private val pollingIntervalMs: Long,
  @Value("\${pollers.topApplicationExecutionCleanup.threshold:2000}") private val threshold: Int,
  @Value("\${pollers.topApplicationExecutionCleanup.chunkSize:1}") private val chunkSize: Int
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(TopApplicationExecutionCleanupPollingNotificationAgent::class.java)

  private val deletedId: Id
  private val errorsCounter: Counter
  private val invocationTimer: LongTaskTimer

  private val completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }

  init {
    deletedId = registry.createId("pollers.topApplicationExecutionCleanup.deleted")
    errorsCounter = registry.counter("pollers.topApplicationExecutionCleanup.errors")

    invocationTimer = com.netflix.spectator.api.patterns.LongTaskTimer.get(
      registry, registry.createId("pollers.topApplicationExecutionCleanup.timing")
    )
  }

  override fun getPollingInterval(): Long {
    return pollingIntervalMs
  }

  override fun getNotificationType(): String {
    return TopApplicationExecutionCleanupPollingNotificationAgent::class.java.simpleName
  }

  override fun tick() {
    val timerId = invocationTimer.start()
    val startTime = System.currentTimeMillis()

    try {
      log.info("Agent {} started", notificationType)
      performCleanup()
    } catch (e: Exception) {
      log.error("Agent {} failed to perform cleanup", e)
    } finally {
      log.info("Agent {} completed in {}ms", notificationType, System.currentTimeMillis() - startTime)
      invocationTimer.stop(timerId)
    }
  }

  private fun performCleanup() {
    val applicationsWithOldOrchestrations = jooq
      .select(DSL.field("application"), DSL.count(DSL.field("id")).`as`("count"))
      .from(DSL.table("orchestrations"))
      .groupBy(DSL.field("application"))
      .having(DSL.count(DSL.field("id")).gt(threshold))
      .fetch()

    applicationsWithOldOrchestrations.forEach {
      val application = it.getValue(DSL.field("application")) as String? ?: return@forEach

      try {
        val startTime = System.currentTimeMillis()

        log.debug("Cleaning up old orchestrations for $application")
        val deletedOrchestrationCount = performCleanup(application)
        log.debug(
          "Cleaned up {} old orchestrations for {} in {}ms",
          deletedOrchestrationCount,
          application,
          System.currentTimeMillis() - startTime
        )
      } catch (e: Exception) {
        log.error("Failed to cleanup old orchestrations for $application", e)
        errorsCounter.increment();
      }
    }
  }

  /**
   * An application can have at most [threshold] completed orchestrations.
   */
  private fun performCleanup(application: String) : Int {
    val deletedExecutionCount = AtomicInteger()

    val executionsToRemove = jooq
      .select(DSL.field("id"))
      .from(DSL.table("orchestrations"))
      .where(
        DSL.field("application").eq(application).and(
          "status IN (${completedStatuses.joinToString(",") { "'$it'" }})"
        )
      )
      .orderBy(DSL.field("build_time").desc())
      .limit(threshold, Int.MAX_VALUE)
      .fetch()
      .map { it.getValue(DSL.field("id")) }

    log.debug("Found {} old orchestrations for {}", executionsToRemove.size, application)

    executionsToRemove.chunked(chunkSize).forEach { ids ->
      deletedExecutionCount.addAndGet(ids.size)
      jooq.transactional(retrySupport) {
        it.delete(DSL.table("correlation_ids")).where(
          "orchestration_id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
        it.delete(DSL.table("orchestration_stages")).where(
          "execution_id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
        it.delete(DSL.table("orchestrations")).where(
          "id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
      }

      registry.counter(deletedId.withTag("application", application)).add(ids.size.toDouble())
    }

    return deletedExecutionCount.toInt()
  }
}
