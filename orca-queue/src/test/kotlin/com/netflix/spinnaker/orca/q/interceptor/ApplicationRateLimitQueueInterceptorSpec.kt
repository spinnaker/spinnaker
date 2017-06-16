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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import java.time.Duration

object ApplicationRateLimitQueueInterceptorSpec : Spek({
  val backend: RateLimitBackend = mock()
  val registry = NoopRegistry()
  val props = TrafficShapingProperties.ApplicationRateLimitingProperties()

  describe("an application rate limit queue interceptor") {
    val message = StartStage(Pipeline::class.java, "1", "foo", "1")
    val subject = ApplicationRateLimitQueueInterceptor(backend, registry, props)

    describe("when learning") {
      describe("when limited no callback is returned") {
        whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ZERO, enforcing = false)
        assertNull(subject.interceptMessage(message))
      }

      describe("when not limited, no callback is returned") {
        whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = false, duration = Duration.ZERO, enforcing = false)
        assertNull(subject.interceptMessage(message))
      }
    }

    describe("when enforcing") {
      describe("when limited callback is returned") {
        whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ZERO, enforcing = true)
        assertNotNull(subject.interceptMessage(message))
      }

      describe("when not limited, no callback is returned") {
        whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = false, duration = Duration.ZERO, enforcing = true)
        assertNull(subject.interceptMessage(message))
      }
    }
  }
})
