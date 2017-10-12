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

package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.MDC
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PostConstruct
import com.netflix.spinnaker.security.AuthenticatedRequest.SPINNAKER_EXECUTION_ID

@Component
class QueueProcessor(
  private val queue: Queue,
  private val queueExecutor: QueueExecutor,
  private val registry: Registry,
  private val handlers: Collection<MessageHandler<*>>
) : DiscoveryActivated {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)

  private val pollOpsRateId = registry.createId("orca.nu.worker.pollOpsRate")
  private val pollErrorRateId = registry.createId("orca.nu.worker.pollErrorRate")
  private val pollSkippedNoCapacity = registry.createId("orca.nu.worker.pollSkippedNoCapacity")
  private val pollRejectedMessage = registry.createId("orca.nu.worker.pollRejectedMessage")

  @Scheduled(fixedDelayString = "\${queue.poll.frequency.ms:10}")
  fun pollOnce() =
    ifEnabled {
      if (!queueExecutor.hasCapacity()) {
        registry.counter(pollSkippedNoCapacity).increment()
      } else {
        registry.counter(pollOpsRateId).increment()
        queue.poll { message, ack ->
          log.info("Received message $message")
          val handler = handlerFor(message)
          if (handler != null) {
            try {
              queueExecutor.executor.execute {
                if (message is ExecutionLevel) {
                  MDC.put(SPINNAKER_EXECUTION_ID, message.executionId)
                }
                handler.invoke(message)
                ack.invoke()
              }
            } catch (e: RejectedExecutionException) {
              log.warn("Executor at capacity, immediately re-queuing message", e)
              queue.push(message)
              registry.counter(pollRejectedMessage).increment()
            } finally {
              MDC.remove(SPINNAKER_EXECUTION_ID)
            }
          } else {
            registry.counter(pollErrorRateId).increment()

            // TODO: DLQ
            throw IllegalStateException("Unsupported message type ${message.javaClass.simpleName}: $message")
          }
        }
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

  @PostConstruct fun confirmQueueType() =
    log.info("Using ${queue.javaClass.simpleName} queue")
}
