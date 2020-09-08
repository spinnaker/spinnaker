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

package com.netflix.spinnaker.q.metrics

import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue

/**
 * Optional interface [Queue] implementations may support in order to provide
 * hooks and publish metrics
 */
interface MonitorableQueue : Queue {

  val publisher: EventPublisher

  /**
   * @return the current state of the queue.
   */
  fun readState(): QueueState

  /**
   * Confirms if the queue currently contains one or more messages matching
   * [predicate].
   */
  fun containsMessage(predicate: (Message) -> Boolean): Boolean

  /**
   * Convenience method to allow implementations to fire events.
   */
  fun fire(event: QueueEvent) {
    publisher.publishEvent(event)
  }
}

data class QueueState(
  /**
   * Number of messages currently queued for delivery including any not yet due.
   */
  val depth: Int,
  /**
   * Number of messages ready for delivery.
   */
  val ready: Int,
  /**
   * Number of messages currently being processed but not yet acknowledged.
   */
  val unacked: Int,
  /**
   * Number of messages neither queued or in-process.
   *
   * Some implementations may not have any way to implement this metric. It is
   * only intended for alerting leaks.
   */
  val orphaned: Int = 0,
  /**
   * Difference between number of known message hashes and number of de-dupe
   * hashes plus in-process messages.
   *
   * Some implementations may not have any way to implement this metric. It is
   * only intended for alerting leaks.
   */
  val hashDrift: Int = 0
)
