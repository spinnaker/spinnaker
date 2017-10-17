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

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.q.ApplicationAware
import com.netflix.spinnaker.orca.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

/**
 * Monitors a queue and generates Atlas metrics.
 */
@Component
@ConditionalOnBean(MonitorableQueue::class)
open class AtlasQueueMonitor
@Autowired constructor(
  @Qualifier("queueImpl") private val queue: MonitorableQueue,
  private val registry: Registry,
  private val clock: Clock
) : ApplicationListener<QueueEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun onApplicationEvent(event: QueueEvent) {
    when (event) {
      is QueuePolled -> _lastQueuePoll.set(event.instant)
      is RetryPolled -> _lastRetryPoll.set(event.instant)
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

  @PostConstruct fun registerGauges() {
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
      is ApplicationAware -> registry.counter("queue.pushed.messages", "application", payload.application)
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
}
