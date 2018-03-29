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
import com.netflix.spinnaker.orca.q.TotalThrottleTimeAttribute
import com.netflix.spinnaker.orca.q.trafficshaping.InterceptorType
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptorCallback
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitBackend
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitContext
import com.netflix.spinnaker.q.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Throttles requests globally.
 */
class GlobalRateLimitQueueInterceptor(
  private val backend: RateLimitBackend,
  private val registry: Registry,
  private val properties: TrafficShapingProperties.GlobalRateLimitingProperties,
  private val timeShapedId: Id
) : TrafficShapingInterceptor {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val throttledMessagesId = registry.createId("queue.trafficShaping.globalRateLimit.throttledMessages")

  override fun getName() = "globalRateLimit"
  override fun supports(type: InterceptorType): Boolean = type == InterceptorType.MESSAGE
  override fun interceptPoll(): Boolean = false
  override fun getPriority() = properties.priority

  override fun interceptMessage(message: Message): TrafficShapingInterceptorCallback? {
    val rateLimit: RateLimit
    try {
      rateLimit = backend.incrementAndGet(
        "globalQueue",
        RateLimitContext(
          getName(),
          properties.capacity,
          !properties.learning,
          properties.durationMs
        )
      )
    } catch (e: Exception) {
      log.error("Rate limiting backend threw exception, disabling interceptor for message", e)
      return null
    }

    if (rateLimit.limiting) {
      if (rateLimit.enforcing) {
        log.info("Throttling message: $message")
        return { queue, msg, ack ->
          msg.setAttribute(msg.getAttribute() ?: TotalThrottleTimeAttribute()).add(rateLimit.duration.toMillis())
          queue.push(message, rateLimit.duration)
          ack.invoke()
          val app = when (msg) {
            is ApplicationAware -> msg.application
            else -> "UNKNOWN"
          }
          registry.counter(throttledMessagesId.withTags("learning", "false", "application", app)).increment()
          registry.counter(timeShapedId.withTags("interceptor", getName(), "application", app)).increment(rateLimit.duration.toMillis())
        }
      }
      registry.counter(throttledMessagesId.withTag("learning", "true")).increment()
    }

    return null
  }

}
