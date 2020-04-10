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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ExecutionLevel
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.MINUTES
import java.util.Optional
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import rx.Scheduler
import rx.schedulers.Schedulers

/**
 * Monitors a queue and generates Atlas metrics.
 */
@Component
@ConditionalOnExpression(
  "\${queue.zombie-check.enabled:false}")
@ConditionalOnBean(MonitorableQueue::class)
class ZombieExecutionCheckingAgent
@Autowired constructor(
  private val queue: MonitorableQueue,
  private val registry: Registry,
  private val repository: ExecutionRepository,
  private val clock: Clock,
  private val conch: NotificationClusterLock,
  @Qualifier("scheduler") private val zombieCheckScheduler: Optional<Scheduler>,
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
      val criteria = ExecutionRepository.ExecutionCriteria().setStatuses(RUNNING)
      repository.retrieve(PIPELINE, criteria)
        .mergeWith(repository.retrieve(ORCHESTRATION, criteria))
        .subscribeOn(zombieCheckScheduler.orElseGet(Schedulers::io))
        .filter(this::hasBeenAroundAWhile)
        .filter(this::queueHasNoMessages)
        .doOnCompleted {
          log.info("Completed zombie check in ${Duration.between(startedAt, clock.instant())}")
        }
        .subscribe {
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

  private fun hasBeenAroundAWhile(execution: PipelineExecution): Boolean =
    Instant.ofEpochMilli(execution.buildTime!!)
      .isBefore(clock.instant().minus(zombieCheckCutoffMinutes, MINUTES))

  private fun queueHasNoMessages(execution: PipelineExecution): Boolean =
    !queue.containsMessage { message ->
      message is ExecutionLevel && message.executionId == execution.id
    }
}
