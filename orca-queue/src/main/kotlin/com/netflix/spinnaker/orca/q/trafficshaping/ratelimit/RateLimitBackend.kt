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
package com.netflix.spinnaker.orca.q.trafficshaping.ratelimit

import java.time.Duration

data class RateLimit(val limiting: Boolean, val duration: Duration, val enforcing: Boolean)

data class RateLimitContext(
  val namespace: String,
  val capacity: Int,
  val enforcing: Boolean,
  val duration: Long
)

interface RateLimitBackend {
  /**
   * If the given application's executions are determined to be rate limited,
   * an associated duration should be provided for how long the message should
   * be delayed.
   */
  fun incrementAndGet(subject: String, context: RateLimitContext): RateLimit
}

class NoopRateLimitBackend : RateLimitBackend {
  override fun incrementAndGet(subject: String, context: RateLimitContext) = RateLimit(false, Duration.ZERO, false)
}
