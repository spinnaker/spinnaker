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
package com.netflix.spinnaker.orca.q.interceptor

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.q.InterceptorType
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.TrafficShapingInterceptorCallback
import com.netflix.spinnaker.orca.q.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Throttles requests globally.
 */
class GlobalRateLimitQueueInterceptor(
  private val backend: RateLimitBackend,
  private val registry: Registry,
  private val properties: TrafficShapingProperties.GlobalRateLimitingProperties
) : TrafficShapingInterceptor {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val throttledMessagesId = registry.createId("queue.trafficShaping.throttledMessages")

  override fun getName() = "globalRateLimit"
  override fun supports(type: InterceptorType): Boolean = type == InterceptorType.MESSAGE
  override fun interceptPoll(): Boolean = false

  override fun interceptMessage(message: Message): TrafficShapingInterceptorCallback? {
    val rateLimit: RateLimit
    try {
      rateLimit = backend.incrementAndGet(
        "globalQueue",
        RateLimitContext(
          getName(),
          properties.capacity,
          !properties.learning
        )
      )
    } catch (e: Exception) {
      log.error("Rate limiting backend threw exception, disabling interceptor for message", e)
      return null
    }

    if (rateLimit.limiting) {
      if (rateLimit.enforcing) {
        log.info("Throttling message: $message")
        return { queue, message, ack ->
          queue.push(message, rateLimit.duration)
          ack.invoke()
          registry.counter(throttledMessagesId).increment()
        }
      }
      log.info("Would have throttled message, but learning-mode enabled: $message")
    }

    return null
  }

}
