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

package com.netflix.spinnaker.q

import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.metrics.NoopEventPublisher
import com.netflix.spinnaker.q.metrics.QueueState
import java.time.Duration
import java.time.temporal.TemporalAmount
import org.slf4j.LoggerFactory

/**
 * A Noop queue to be used when no Queue bean is found (e.g. when Queue is disabled)
 */
class NoopQueue : MonitorableQueue {
  override val publisher: EventPublisher = NoopEventPublisher()
  private val log = LoggerFactory.getLogger(this.javaClass)

  init {
    log.warn(
      "${this.javaClass.simpleName} was created - all queue operations will be NOOP'd. " +
        "This is OK if the queue was intended to be disabled"
    )
  }

  override val ackTimeout: TemporalAmount
    get() = Duration.ofMinutes(1)
  override val canPollMany: Boolean
    get() = false
  override val deadMessageHandlers: List<DeadMessageCallback>
    get() = emptyList()

  override fun ensure(message: Message, delay: TemporalAmount) {
  }

  override fun poll(callback: QueueCallback) {
  }

  override fun poll(maxMessages: Int, callback: QueueCallback) {
  }

  override fun push(message: Message, delay: TemporalAmount) {
    log.warn(
      "A message ({}) was pushed onto the NoopQueue - this is probably not the intent",
      message
    )
  }

  override fun reschedule(message: Message, delay: TemporalAmount) {
  }

  override fun containsMessage(predicate: (Message) -> Boolean) = false
  override fun readState(): QueueState = QueueState(0, 0, 0)
}
