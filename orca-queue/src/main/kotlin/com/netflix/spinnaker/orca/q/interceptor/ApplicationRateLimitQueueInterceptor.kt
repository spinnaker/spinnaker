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
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Rate limits messages by application. It's up to the backend to determine how to implement the storage and specific
 * rate limiting logic.
 */
class ApplicationRateLimitQueueInterceptor(
  val backend: RateLimitBackend,
  val registry: Registry,
  val applicationRateLimitingProperties: TrafficShapingProperties.ApplicationRateLimitingProperties
): TrafficShapingInterceptor {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val throttledMessagesId = registry.createId("queue.trafficShaping.appRateLimit.throttledMessages")

  override fun getName() = "appRateLimit"
  override fun supports(type: InterceptorType) = type == InterceptorType.MESSAGE
  override fun interceptPoll() = false

  override fun interceptMessage(message: Message): TrafficShapingInterceptorCallback? {
    when (message) {
      !is ApplicationAware -> return null
      else -> {
        val rateLimit: RateLimit
        try {
          rateLimit = backend.incrementAndGet(
            message.application,
            RateLimitContext(
              getName(),
              applicationRateLimitingProperties.getCapacity(message.application),
              applicationRateLimitingProperties.getEnforcing(message.application)
            )
          )
        } catch (e: Exception) {
          log.error("Rate limiting backend threw exception, disabling interceptor for message", e);
          return null
        }
        if (rateLimit.limiting) {
          if (rateLimit.enforcing) {
            log.info("Throttling message: $message")
            return { queue, msg, ack ->
              queue.push(msg, rateLimit.duration)
              ack.invoke()
              registry.counter(throttledMessagesId.withTag("learning", "false").withTag("application", message.application)).increment()
            }
          }
          log.info("Would have throttled message for ${message.application}, but learning-mode enabled: $message")
          registry.counter(throttledMessagesId.withTag("learning", "true").withTag("application", message.application)).increment()
        }
        return null
      }
    }
  }
}
