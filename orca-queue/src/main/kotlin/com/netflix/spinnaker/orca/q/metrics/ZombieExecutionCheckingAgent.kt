/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.q.ZombieExecutionService
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import java.time.Clock
import java.time.Duration
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

/**
 * Monitors a queue and generates Atlas metrics.
 */
@Component
@ConditionalOnExpression(
  "\${queue.zombie-check.enabled:false}"
)
@ConditionalOnBean(MonitorableQueue::class)
class ZombieExecutionCheckingAgent(
  private val zombieExecutionService: ZombieExecutionService,
  private val registry: Registry,
  private val clock: Clock,
  conch: NotificationClusterLock,
  @Value("\${queue.zombie-check.interval-ms:3600000}") private val pollingIntervalMs: Long,
  @Value("\${queue.zombie-check.enabled:false}") private val zombieCheckEnabled: Boolean,
  @Value("\${queue.zombie-check.cutoff-minutes:10}") private val zombieCheckCutoffMinutes: Long,
  @Value("\${keiko.queue.enabled:true}") private val queueEnabled: Boolean
) : AbstractPollingNotificationAgent(conch) {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun getPollingInterval() = pollingIntervalMs

  override fun getNotificationType() = javaClass.getSimpleName()

  override fun tick() {
    checkForZombies()
  }

  fun checkForZombies() {
    if (!queueEnabled) {
      return
    }

    if (!zombieCheckEnabled) {
      log.info("Not running zombie check: checkEnabled: $zombieCheckEnabled")
      return
    }

    try {
      MDC.put(AGENT_MDC_KEY, this.javaClass.simpleName)
      val startedAt = clock.instant()
      val zombies = zombieExecutionService.findAllZombies(Duration.ofMinutes(zombieCheckCutoffMinutes))
      log.info("Completed zombie check in ${Duration.between(startedAt, clock.instant())}")
      zombies.forEach {
        log.error(
          "Found zombie {} {} {} {}",
          kv("executionType", it.type),
          kv("application", it.application),
          kv("executionName", it.name),
          kv("executionId", it.id)
        )
        val tags = mutableListOf<Tag>(
          BasicTag("application", it.application),
          BasicTag("type", it.type.name)
        )
        registry.counter("queue.zombies", tags).increment()
      }
    } finally {
      MDC.remove(AGENT_MDC_KEY)
    }
  }
}
