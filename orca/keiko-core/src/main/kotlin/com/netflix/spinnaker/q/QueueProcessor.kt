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

package com.netflix.spinnaker.q

import com.netflix.spinnaker.KotlinOpen
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.HandlerThrewError
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.NoHandlerCapacity
import java.time.Duration
import java.util.Random
import java.util.concurrent.RejectedExecutionException
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.scheduling.annotation.Scheduled

/**
 * The processor that fetches messages from the [Queue] and hands them off to
 * the appropriate [MessageHandler].
 */
@KotlinOpen
class QueueProcessor(
  private val queue: Queue,
  private val executor: QueueExecutor<*>,
  private val handlers: Collection<MessageHandler<*>>,
  private val activators: List<Activator>,
  private val publisher: EventPublisher,
  private val deadMessageHandler: DeadMessageCallback,
  private val fillExecutorEachCycle: Boolean = true,
  private val requeueDelay: Duration = Duration.ofSeconds(0),
  private val requeueMaxJitter: Duration = Duration.ofSeconds(0)
) {
  private val log: Logger = getLogger(javaClass)
  private val random: Random = Random()

  /**
   * Polls the [Queue] once (or more if [fillExecutorEachCycle] is true) so
   * long as [executor] has capacity.
   */
  @Scheduled(fixedDelayString = "\${queue.poll.frequency.ms:50}")
  fun poll() =
    ifEnabled {
      if (executor.hasCapacity()) {
        if (fillExecutorEachCycle) {
          if (queue.canPollMany) {
            queue.poll(executor.availableCapacity(), callback)
          } else {
            executor.availableCapacity().downTo(1).forEach {
              pollOnce()
            }
          }
        } else {
          pollOnce()
        }
      } else {
        publisher.publishEvent(NoHandlerCapacity)
      }
    }

  /**
   * Polls the [Queue] once to attempt to read a single message.
   */
  private fun pollOnce() {
    queue.poll(callback)
  }

  val callback: QueueCallback = { message, ack ->
    log.info("Received message $message")
    val handler = handlerFor(message)
    if (handler != null) {
      try {
        executor.execute {
          try {
            QueueContextHolder.set(message)
            handler.invoke(message)
            ack.invoke()
          } catch (e: Throwable) {
            // Something very bad is happening
            log.error("Unhandled throwable from $message", e)
            publisher.publishEvent(HandlerThrewError(message))
          } finally {
            QueueContextHolder.clear()
          }
        }
      } catch (e: RejectedExecutionException) {
        var requeueDelaySeconds = requeueDelay.seconds
        if (requeueMaxJitter.seconds > 0) {
          requeueDelaySeconds += random.nextInt(requeueMaxJitter.seconds.toInt())
        }

        val requeueDelay = Duration.ofSeconds(requeueDelaySeconds)
        val numberOfAttempts = message.getAttribute<AttemptsAttribute>()

        log.warn(
          "Executor at capacity, re-queuing message {} (delay: {}, attempts: {})",
          message,
          requeueDelay,
          numberOfAttempts,
          e
        )
        queue.push(message, requeueDelay)
      }
    } else {
      log.error("Unsupported message type ${message.javaClass.simpleName}: $message")
      deadMessageHandler.invoke(queue, message)
      publisher.publishEvent(MessageDead)
    }
  }

  private fun ifEnabled(fn: () -> Unit) {
    if (activators.all { it.enabled }) {
      fn.invoke()
    }
  }

  private val handlerCache = mutableMapOf<Class<out Message>, MessageHandler<*>>()

  private fun handlerFor(message: Message) =
    handlerCache[message.javaClass]
      .let { handler ->
        handler ?: handlers
          .find { it.messageType.isAssignableFrom(message.javaClass) }
          ?.also { handlerCache[message.javaClass] = it }
      }

  @PostConstruct
  fun confirmQueueType() =
    log.info("Using queue $queue")
}
