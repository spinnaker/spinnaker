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
import com.netflix.spinnaker.orca.q.TotalThrottleTimeAttribute
import com.netflix.spinnaker.orca.q.trafficshaping.InterceptorType
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.trafficshaping.TrafficShapingInterceptorCallback
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitBackend
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Rate limits messages by application. It's up to the backend to determine how to implement the storage and specific
 * rate limiting logic.
 */
class ApplicationRateLimitQueueInterceptor(
  private val backend: RateLimitBackend,
  private val registry: Registry,
  private val applicationRateLimitingProperties: TrafficShapingProperties.ApplicationRateLimitingProperties,
  private val timeShapedId: Id
): TrafficShapingInterceptor {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val throttledMessagesId = registry.createId("queue.trafficShaping.appRateLimit.throttledMessages")

  override fun getName() = "appRateLimit"
  override fun supports(type: InterceptorType) = type == InterceptorType.MESSAGE
  override fun interceptPoll() = false
  override fun getPriority() = applicationRateLimitingProperties.priority

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
              applicationRateLimitingProperties.getEnforcing(message.application),
              applicationRateLimitingProperties.durationMs
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
              msg.setAttribute(msg.getAttribute<TotalThrottleTimeAttribute>(TotalThrottleTimeAttribute())).add(rateLimit.duration.toMillis())
              queue.push(msg, rateLimit.duration)
              ack.invoke()
              registry.counter(throttledMessagesId.withTags("learning", "false", "application", message.application)).increment()
              registry.counter(timeShapedId.withTags("application", message.application, "interceptor", getName())).increment(rateLimit.duration.toMillis())
            }
          }
          registry.counter(throttledMessagesId.withTags("learning", "true", "application", message.application)).increment()
        }
        return null
      }
    }
  }
}
