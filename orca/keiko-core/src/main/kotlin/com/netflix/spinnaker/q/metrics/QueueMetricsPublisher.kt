/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.q.metrics

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * - can be registered as a queue EventPublisher
 * - publishes metrics based on queue events
 */
class QueueMetricsPublisher(
  val registry: Registry,
  val clock: Clock
) : EventPublisher {
  init {
    PolledMeter.using(registry)
      .withName("queue.last.poll.age")
      .monitorValue(
        this,
        {
          Duration
            .between(it.lastQueuePoll, clock.instant())
            .toMillis()
            .toDouble()
        }
      )

    PolledMeter.using(registry)
      .withName("queue.last.retry.check.age")
      .monitorValue(
        this,
        {
          Duration
            .between(it.lastRetryPoll, clock.instant())
            .toMillis()
            .toDouble()
        }
      )
  }

  override fun publishEvent(event: QueueEvent) {
    when (event) {
      QueuePolled -> _lastQueuePoll.set(clock.instant())
      is MessageProcessing -> {
        registry.timer("queue.message.lag").record(event.lag.toMillis(), TimeUnit.MILLISECONDS)
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

  /**
   * Count of messages pushed to the queue.
   */
  private val MessagePushed.counter: Counter
    get() = registry.counter("queue.pushed.messages")

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
    get() = registry.counter(
      "queue.duplicate.messages",
      "messageType", payload.javaClass.simpleName
    )

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
}
