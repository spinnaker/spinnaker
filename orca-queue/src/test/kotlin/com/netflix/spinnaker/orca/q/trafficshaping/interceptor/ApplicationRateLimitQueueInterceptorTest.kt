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
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.TotalThrottleTimeAttribute
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitBackend
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.junit.Assert.*
import java.time.Clock
import java.time.Duration

object ApplicationRateLimitQueueInterceptorTest : Spek({
  val backend: RateLimitBackend = mock()
  val registry = NoopRegistry()
  val props = TrafficShapingProperties.ApplicationRateLimitingProperties()
  val timeShapedId: Id = mock()

  describe("an application rate limit queue interceptor") {
    val message = StartStage(PIPELINE, "1", "foo", "1")
    val subject = ApplicationRateLimitQueueInterceptor(backend, registry, props, timeShapedId)

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
      describe("when limited") {

        val queueImpl: Queue = InMemoryQueue(Clock.systemDefaultZone(), deadMessageHandler = mock(), publisher = mock())

        describe("callback is returned") {
          whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ZERO, enforcing = true)
          assertNotNull(subject.interceptMessage(message))
        }

        describe("callback message contains throttle time") {
          val msg: Message = StartStage(PIPELINE, "1", "foo", "1")
          whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ZERO, enforcing = true)
          subject.interceptMessage(msg)?.invoke(queueImpl, msg, {})
          assertNotNull(msg.getAttribute<TotalThrottleTimeAttribute>())
        }

        describe("throttle time is being set") {
          val msg: Message = StartStage(PIPELINE, "1", "foo", "1")
          whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ofMillis(5), enforcing = true)
          subject.interceptMessage(msg)?.invoke(queueImpl, msg, {})
          assertEquals(5L, msg.getAttribute<TotalThrottleTimeAttribute>()?.totalThrottleTimeMs)
        }

        describe("throttle time is being added") {
          val msg: Message = StartStage(PIPELINE, "1", "foo", "1")
          whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = true, duration = Duration.ofMillis(5), enforcing = true)
          subject.interceptMessage(msg)?.invoke(queueImpl, msg, {})
          subject.interceptMessage(msg)?.invoke(queueImpl, msg, {})
          assertEquals(10L, msg.getAttribute<TotalThrottleTimeAttribute>()?.totalThrottleTimeMs)
        }
      }
    }

    describe("when not limited, no callback is returned") {
      whenever(backend.incrementAndGet(any(), any())) doReturn RateLimit(limiting = false, duration = Duration.ZERO, enforcing = true)
      assertNull(subject.interceptMessage(message))
    }
  }
})
