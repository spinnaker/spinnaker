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
package com.netflix.spinnaker.orca.q.trafficshaping.interceptor

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.q.ApplicationAware
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.trafficshaping.InterceptorType
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptorCallback
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.GlobalCapacity
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.PrioritizationStrategy
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.Priority
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.PriorityCapacityRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Throttles messages by priority. If the system reaches a ceiling of
 * concurrent active executions, lower priority messages will have a
 * higher chance of being throttled to keep a higher quality of service
 * for more important messages.
 *
 * Prioritization is assigned via an PrioritizationStrategy
 * out of band of actual queue processing.
 */
class PriorityCapacityQueueInterceptor(
  private val repository: PriorityCapacityRepository,
  private val prioritizationStrategy: PrioritizationStrategy,
  private val registry: Registry,
  private val properties: TrafficShapingProperties.PriorityCapacityProperties,
  private val timeShapedId: Id
  ) : TrafficShapingInterceptor {

  companion object {
    private val THROTTLE_TIME = Duration.of(5, ChronoUnit.SECONDS)
  }

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val r: Random = Random()

  private val throttledMessagesId = registry.createId("queue.trafficShaping.priorityCapacity.throttledMessages")

  override fun getName() = "priorityCapacity"
  override fun supports(type: InterceptorType) = type == InterceptorType.MESSAGE
  override fun interceptPoll() = false
  override fun getPriority() = properties.priority

  override fun interceptMessage(message: Message): TrafficShapingInterceptorCallback? {
    val cap: GlobalCapacity
    try {
      cap = repository.getGlobalCapacity()
    } catch (e: Exception) {
      log.error("ScoredCapacityBackend threw exception getting global capacity, disabling interceptor for message", e)
      return null
    }

    if (!cap.shouldShedLoad()) {
      return null
    }

    val callback = when (message) {
      !is ApplicationAware -> handleUnknownMessage(message)
      else -> handleApplicationMessage(message)
    }

    if (callback != null) {
      if (isLearning(cap.learning)) {
        registry.counter(throttledMessagesId.withTag("learning", "true")).increment()
        return null
      }
      registry.counter(throttledMessagesId.withTag("learning", "false")).increment()
    }

    return callback
  }

  private fun handleUnknownMessage(message: Message): TrafficShapingInterceptorCallback? {
    log.info("Global capacity at maximum limit: Throttling unknown message: $message")
    return defaultThrottleCallback()
  }

  private fun handleApplicationMessage(message: ApplicationAware): TrafficShapingInterceptorCallback? {
    val priority: Priority
    try {
      priority = prioritizationStrategy.getPriority(message)
    } catch (e: Exception) {
      log.error("Failed determining message priority, disabling interceptor for message", e)
      return null
    }

    // TODO rz - configuration of throttle chances
    return when (priority) {
      Priority.CRITICAL -> null
      Priority.HIGH -> if (r.nextInt(4) == 0) defaultThrottleCallback() else null
      Priority.MEDIUM -> if (r.nextInt(1) == 0) defaultThrottleCallback() else null
      Priority.LOW -> defaultThrottleCallback()
    }
  }

  // TODO rz - configuration of throttle duration
  private fun defaultThrottleCallback(): TrafficShapingInterceptorCallback = { queue, msg, ack ->
    queue.push(msg, THROTTLE_TIME)
    val app = when (msg) {
      is ApplicationAware -> msg.application
      else -> "UNKNOWN"
    }
    registry.counter(timeShapedId.withTags("interceptor", getName(), "application", app)).increment(THROTTLE_TIME.toMillis())
    ack.invoke()
  }

  private fun isLearning(capLearningFlag: Boolean?) = capLearningFlag ?: properties.learning
}
