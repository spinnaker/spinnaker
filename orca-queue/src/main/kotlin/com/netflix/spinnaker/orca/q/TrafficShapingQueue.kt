/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.io.Closeable
import java.time.temporal.TemporalAmount

/**
 * TrafficShapingQueue provides a pluggable interface for manipulating queue polling behavior.
 */
@ConditionalOnProperty("queue.trafficShaping.enabled")
@Primary @Component open class TrafficShapingQueue
@Autowired constructor(
  val queueImpl: Queue,
  val registry: Registry,
  interceptors: Collection<TrafficShapingInterceptor>
) : Queue, Closeable {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  override val ackTimeout = queueImpl.ackTimeout
  override val deadMessageHandler: (Queue, Message) -> Unit = queueImpl.deadMessageHandler

  val pollInterceptors = interceptors.filter { it.supports(InterceptorType.POLL) }.sortedBy { it.getPriority() }
  val messageInterceptors = interceptors.filter { it.supports(InterceptorType.MESSAGE) }.sortedBy { it.getPriority() }

  val queueInterceptionsId: Id = registry.createId("queue.trafficShaping.queueInterceptions")
  val messageInterceptionsId: Id = registry.createId("queue.trafficShaping.messageInterceptions")

  init {
    log.info("Starting with interceptors: ${interceptors.map { it.getName() }.joinToString()}")
  }

  override fun poll(callback: QueueCallback) {
    val pollIntercepting = pollInterceptors.filter { it ->
      try {
        return@filter it.interceptPoll()
      } catch (e: Exception) {
        log.error("TrafficShapingInterceptor '${it.getName()}' threw exception: Swallowing", e)
      }
      return@filter false
    }.map { it.getName() }

    if (pollIntercepting.isNotEmpty()) {
      registry.counter(queueInterceptionsId).increment()
      return
    }

    queueImpl.poll { message, ack ->
      val messageIntercepting = messageInterceptors.map { it ->
        try {
          return@map it.interceptMessage(message)
        } catch (e: Exception) {
          log.error("TrafficShapingInterceptor '${it.getName()}' threw exception: Swallowing", e)
        }
        return@map null
      }.filterNotNull()

      if (messageIntercepting.isEmpty()) {
        callback.invoke(message, ack)
      } else {
        messageIntercepting.first().invoke(queueImpl, message, ack)
        registry.counter(messageInterceptionsId).increment()
      }
    }
  }

  override fun push(message: Message, delay: TemporalAmount) = queueImpl.push(message, delay)

  override fun retry() {
    queueImpl.retry()
  }

  override fun close() {
    if (queueImpl is Closeable) {
      queueImpl.close()
    }
  }
}

/**
 * The callback that will interceptors use to alter the queue as necessary. This is similar to the QueueCallback
 * typealias, but with direct access to the backing queue implementation.
 */
typealias TrafficShapingInterceptorCallback = (Queue, Message, () -> Unit) -> Unit

enum class InterceptorType {
  POLL, MESSAGE
}

/**
 * TrafficShapingInterceptors can intercept the entire queue polling mechanism and/or individual messages. Each
 * interceptor method should return a boolean if the poll operation should proceed.
 */
interface TrafficShapingInterceptor {
  fun getName(): String
  fun supports(type: InterceptorType): Boolean
  fun interceptPoll(): Boolean
  fun interceptMessage(message: Message): TrafficShapingInterceptorCallback?
  fun getPriority(): Int = 0
}

/**
 * An empty traffic shaping interceptor for when no other interceptors have been defined so Spring doesn't throw a fit.
 */
class NoopTrafficShapingInterceptor : TrafficShapingInterceptor {
  override fun getName() = "noop"
  override fun supports(type: InterceptorType) = false
  override fun interceptPoll() = false
  override fun interceptMessage(message: Message) = null
}
