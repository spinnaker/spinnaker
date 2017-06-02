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

import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.metrics.QueueEvent.*
import org.springframework.context.ApplicationEventPublisher

/**
 * Optional interface [Queue] implementations may support in order to provide
 * hooks and publish events for analytics.
 */
interface MonitorableQueue : Queue {

  val publisher: ApplicationEventPublisher

  /**
   * Number of messages currently queued for delivery including any not yet due.
   */
  val queueDepth: Int

  /**
   * Number of messages currently being processed but not yet acknowledged.
   */
  val unackedDepth: Int

  /**
   * Number of messages neither queued or in-process. Some implementations
   * may not have any way to implement this metric. It is only intended for
   * alerting leaks.
   */
  val orphanedMessages: Int
    get() = 0

  /**
   * Number of messages ready for delivery.
   */
  val readyDepth: Int
}

/**
 * Convenience method to allow implementations to fire events.
 */
inline fun <reified E : QueueEvent> MonitorableQueue.fire(): Unit {
  val event = when (E::class) {
    QueuePolled::class -> QueuePolled(this)
    RetryPolled::class -> RetryPolled(this)
    MessagePushed::class -> MessagePushed(this)
    MessageAcknowledged::class -> MessageAcknowledged(this)
    MessageRetried::class -> MessageRetried(this)
    MessageDead::class -> MessageDead(this)
    else -> throw IllegalArgumentException("Unknown event type ${E::class}")
  }
  publisher.publishEvent(event)
}
