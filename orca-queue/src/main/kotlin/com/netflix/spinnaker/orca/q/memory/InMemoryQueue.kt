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

package com.netflix.spinnaker.orca.q.memory

import com.netflix.spinnaker.orca.q.DeadMessageCallback
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.metrics.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.threeten.extra.Temporals.chronoUnit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.*
import java.util.UUID.randomUUID
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS

class InMemoryQueue(
  private val clock: Clock,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandler: DeadMessageCallback,
  override val publisher: ApplicationEventPublisher
) : MonitorableQueue {

  private val log: Logger = getLogger(javaClass)

  private val queue = DelayQueue<Envelope>()
  private val unacked = DelayQueue<Envelope>()

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    fire<QueuePolled>()

    queue.poll()?.let { envelope ->
      unacked.put(envelope.copy(scheduledTime = clock.instant().plus(ackTimeout)))
      callback.invoke(envelope.payload) {
        ack(envelope.id)
        fire<MessageAcknowledged>()
      }
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    val existed = queue.removeIf { it.payload == message }
    queue.put(Envelope(message, clock.instant().plus(delay), clock))
    if (existed) {
      fire<MessageDuplicate>(message)
    } else {
      fire<MessagePushed>(message)
    }
  }

  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    val now = clock.instant()
    fire<RetryPolled>()
    unacked.pollAll { message ->
      if (message.count >= Queue.maxRetries) {
        deadMessageHandler.invoke(this, message.payload)
        fire<MessageDead>()
      } else {
        val existed = queue.removeIf { it.payload == message.payload }
        log.warn("redelivering unacked message ${message.payload}")
        queue.put(message.copy(scheduledTime = now, count = message.count + 1))
        if (existed) {
          fire<MessageDuplicate>(message.payload)
        } else {
          fire<MessageRetried>()
        }
      }
    }
  }

  override fun readState() =
    QueueState(
      depth = queue.size,
      ready = queue.count { it.getDelay(NANOSECONDS) <= 0 },
      unacked = unacked.size
    )

  private fun ack(messageId: UUID) {
    unacked.removeIf { it.id == messageId }
  }

  private fun <T : Delayed> DelayQueue<T>.pollAll(block: (T) -> Unit) {
    var done = false
    while (!done) {
      val value = poll()
      if (value == null) {
        done = true
      } else {
        block.invoke(value)
      }
    }
  }
}

internal data class Envelope(
  val id: UUID,
  val payload: Message,
  val scheduledTime: Instant,
  val clock: Clock,
  val count: Int = 1
) : Delayed {
  constructor(payload: Message, scheduledTime: Instant, clock: Clock) :
    this(randomUUID(), payload, scheduledTime, clock)

  override fun compareTo(other: Delayed) =
    getDelay(MILLISECONDS).compareTo(other.getDelay(MILLISECONDS))

  override fun getDelay(unit: TimeUnit) =
    clock.instant().until(scheduledTime, unit.toChronoUnit())
}

private fun TimeUnit.toChronoUnit() = chronoUnit(this)
