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
import com.netflix.spinnaker.orca.q.Queue
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import java.time.Instant.now
import javax.annotation.PostConstruct

/**
 * Optional interface [Queue] implementations may support in order to provide
 * hooks for analytics.
 */
interface MonitoredQueue : Queue {

  val registry: Registry

  /**
   * Number of messages currently queued for delivery including any not yet due.
   */
  val queueDepth: Int

  /**
   * Number of messages currently being processed but not yet acknowledged.
   */
  val unackedDepth: Int

  /**
   * The last time the queue was polled.
   */
  val lastQueuePoll: Instant?

  /**
   * The time the last [retry] method was executed.
   */
  val lastRetryPoll: Instant?

  /**
   * Count of messages pushed to the queue.
   */
  val pushCounter: Counter
    get() = registry.counter("queue.pushed.messages")

  /**
   * Count of messages successfully processed and acknowledged.
   */
  val ackCounter: Counter
    get() = registry.counter("queue.acknowledged.messages")

  /**
   * Count of messages that have been retried. This does not mean unique
   * messages, so retrying the same message again will still increment this
   * count.
   */
  val retryCounter: Counter
    get() = registry.counter("queue.retried.messages")

  /**
   * Count of messages that have exceeded [Queue.maxRetries] retry
   * attempts and have been sent to the dead message handler.
   */
  val deadMessageCounter: Counter
    get() = registry.counter("queue.dead.messages")

  @PostConstruct fun registerGauges() {
    registry.gauge("queue.depth", this, { it.queueDepth.toDouble() })
    registry.gauge("queue.unacked.depth", this, { it.unackedDepth.toDouble() })
    registry.gauge("queue.last.poll.age", this, { Duration.between(it.lastQueuePoll ?: EPOCH, now()).toMillis().toDouble() })
    registry.gauge("queue.last.retry.check.age", this, { Duration.between(it.lastRetryPoll ?: EPOCH, now()).toMillis().toDouble() })
  }
}
