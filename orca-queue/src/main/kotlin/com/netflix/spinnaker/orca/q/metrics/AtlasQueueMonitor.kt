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
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent.AGENT_MDC_KEY
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ApplicationAware
import com.netflix.spinnaker.orca.q.ExecutionLevel
import com.netflix.spinnaker.q.metrics.LockFailed
import com.netflix.spinnaker.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.MessageDuplicate
import com.netflix.spinnaker.q.metrics.MessageNotFound
import com.netflix.spinnaker.q.metrics.MessageProcessing
import com.netflix.spinnaker.q.metrics.MessagePushed
import com.netflix.spinnaker.q.metrics.MessageRescheduled
import com.netflix.spinnaker.q.metrics.MessageRetried
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.metrics.QueueEvent
import com.netflix.spinnaker.q.metrics.QueuePolled
import com.netflix.spinnaker.q.metrics.QueueState
import com.netflix.spinnaker.q.metrics.RetryPolled
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rx.Scheduler
import rx.schedulers.Schedulers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.MINUTES
import java.util.Optional
import java.util.Queue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

/**
 * Monitors a queue and generates Atlas metrics.
 */
@Component
@ConditionalOnBean(MonitorableQueue::class)
class AtlasQueueMonitor
@Autowired constructor(
  private val queue: MonitorableQueue,
  private val registry: Registry,
  private val repository: ExecutionRepository,
  private val clock: Clock,
  private val conch: NotificationClusterLock,
  @Value("\${queue.zombie-check.enabled:false}")private val zombieCheckEnabled: Boolean,
  @Qualifier("scheduler") private val zombieCheckScheduler: Optional<Scheduler>,
  @Value("\${queue.zombie-check.cutoff-minutes:10}") private val zombieCheckCutoffMinutes: Long
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener
  fun onQueueEvent(event: QueueEvent) {
    when (event) {
      QueuePolled -> _lastQueuePoll.set(clock.instant())
      is MessageProcessing -> {
        registry.timer("queue.message.lag").record(event.lag.toMillis(), MILLISECONDS)
      }
      is RetryPolled -> _lastRetryPoll.set(clock.instant())
      is MessagePushed -> event.counter.increment()
      is MessageAcknowledged -> event.counter.increment()
      is MessageRetried -> event.counter.increment()
      is MessageDead -> event.counter.increment()
      is MessageDuplicate -> event.counter.increment()
      is LockFailed -> event.counter.increment()
      is MessageRescheduled -> event.counter.increment()
      is MessageNotFound -> event.counter.increment()
    }
  }

  @Scheduled(fixedDelayString = "\${queue.depth.metric.frequency:30000}")
  fun pollQueueState() {
    _lastState.set(queue.readState())
  }

  @Scheduled(fixedDelayString = "\${queue.zombie-check.interval-ms:3600000}")
  fun checkForZombies() {
    val lockAcquired = conch.tryAcquireLock("zombie", TimeUnit.MINUTES.toSeconds(5))

    if (!zombieCheckEnabled || !lockAcquired) {
      log.info("Not running zombie check: checkEnabled: $zombieCheckEnabled, lockAcquired: $lockAcquired")
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

  @PostConstruct
  fun registerGauges() {
    log.info("Monitorable queue implementation $queue found. Exporting metrics to Atlas.")

    registry.gauge("queue.depth", this, {
      it.lastState.depth.toDouble()
    })
    registry.gauge("queue.unacked.depth", this, {
      it.lastState.unacked.toDouble()
    })
    registry.gauge("queue.ready.depth", this, {
      it.lastState.ready.toDouble()
    })
    registry.gauge("queue.orphaned.messages", this, {
      it.lastState.orphaned.toDouble()
    })
    registry.gauge("queue.last.poll.age", this, {
      Duration
        .between(it.lastQueuePoll, clock.instant())
        .toMillis()
        .toDouble()
    })
    registry.gauge("queue.last.retry.check.age", this, {
      Duration
        .between(it.lastRetryPoll, clock.instant())
        .toMillis()
        .toDouble()
    })
  }

  /**
   * The last time the [Queue.poll] method was executed.
   */
  val lastQueuePoll: Instant
    get() = _lastQueuePoll.get()
  private val _lastQueuePoll = AtomicReference<Instant>(clock.instant())

  /**
   * The time the last [Queue.retry] method was executed.
   */
  val lastRetryPoll: Instant
    get() = _lastRetryPoll.get()
  private val _lastRetryPoll = AtomicReference<Instant>(clock.instant())

  val lastState: QueueState
    get() = _lastState.get()
  private val _lastState = AtomicReference<QueueState>(QueueState(0, 0, 0))

  /**
   * Count of messages pushed to the queue.
   */
  private val MessagePushed.counter: Counter
    get() = when (payload) {
      is ApplicationAware -> registry.counter("queue.pushed.messages", "application", (payload as ApplicationAware).application)
      else -> registry.counter("queue.pushed.messages")
    }

  /**
   * Count of messages successfully processed and acknowledged.
   */
  private val MessageAcknowledged.counter: Counter
    get() = registry.counter("queue.acknowledged.messages")

  /**
   * Count of messages that have been retried. This does not mean unique
   * messages, so retrying the same message again will still increment this
   * count.
   */
  private val MessageRetried.counter: Counter
    get() = registry.counter("queue.retried.messages")

  /**
   * Count of messages that have exceeded [Queue.maxRetries] retry
   * attempts and have been sent to the dead message handler.
   */
  private val MessageDead.counter: Counter
    get() = registry.counter("queue.dead.messages")

  /**
   * Count of messages that have been pushed or re-delivered while an identical
   * message is already on the queue.
   */
  private val MessageDuplicate.counter: Counter
    get() = registry.counter("queue.duplicate.messages", "messageType", payload.javaClass.simpleName)

  /**
   * Count of attempted message reads that failed to acquire a lock (in other
   * words, multiple Orca instance tried to read the same message).
   */
  private val LockFailed.counter: Counter
    get() = registry.counter("queue.lock.failed")

  /**
   * Count of attempted message rescheduling that succeeded (in other words,
   * that message existed on the queue).
   */
  private val MessageRescheduled.counter: Counter
    get() = registry.counter("queue.reschedule.succeeded")

  /**
   * Count of attempted message rescheduling that failed (in other words,
   * that message did not exist on the queue).
   */
  private val MessageNotFound.counter: Counter
    get() = registry.counter("queue.message.notfound")

  private fun hasBeenAroundAWhile(execution: Execution): Boolean =
    Instant.ofEpochMilli(execution.buildTime!!)
      .isBefore(clock.instant().minus(zombieCheckCutoffMinutes, MINUTES))

  private fun queueHasNoMessages(execution: Execution): Boolean =
    !queue.containsMessage { message ->
      message is ExecutionLevel && message.executionId == execution.id
    }
}
