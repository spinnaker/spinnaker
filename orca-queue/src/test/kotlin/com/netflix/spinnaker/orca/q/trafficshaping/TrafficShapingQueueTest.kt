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
package com.netflix.spinnaker.orca.q.trafficshaping

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueCallback
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Clock

object TrafficShapingQueueTest : SubjectSpek<TrafficShapingQueue>({
  val queueImplCallback: QueueCallback = mock()
  val queueImpl: Queue = InMemoryQueue(Clock.systemDefaultZone(), deadMessageHandler = mock(), publisher = mock())
  val registry: Registry = NoopRegistry()
  val interceptor: TrafficShapingInterceptor = mock()

  subject(CachingMode.GROUP) {
    whenever(interceptor.supports(any())) doReturn true
    TrafficShapingQueue(queueImpl, registry, listOf(interceptor))
  }

  afterGroup {
    reset(queueImplCallback)
  }

  describe("when no interceptors are triggered") {
    beforeGroup {
      whenever(interceptor.interceptPoll()) doReturn false
      whenever(interceptor.interceptMessage(any())).doReturn(null as TrafficShapingInterceptorCallback?)

      subject.push(StartStage(PIPELINE, "1", "foo", "1"))
    }

    action("when the queue is polled") {
      subject.poll(queueImplCallback)
    }

    it("polls the queue impl") {
      verify(queueImplCallback).invoke(any(), any())
    }
  }

  describe("when a poll interceptor is triggered") {
    beforeGroup {
      whenever(interceptor.interceptPoll()) doReturn true

      subject.push(StartStage(PIPELINE, "1", "foo", "1"))
    }

    action("when the queue is polled") {
      subject.poll(queueImplCallback)
    }

    it("intercepts the queue impl poll operation") {
      verifyZeroInteractions(queueImplCallback)
    }
  }

  describe("when a message interceptor is triggered") {
    val interceptorCallback: TrafficShapingInterceptorCallback = mock()

    beforeGroup {
      whenever(interceptor.interceptPoll()) doReturn false
      whenever(interceptor.interceptMessage(any())) doReturn interceptorCallback

      subject.push(StartStage(PIPELINE, "1", "foo", "1"))
    }

    action("when the queue is polled") {
      subject.poll(queueImplCallback)
    }

    it("intercepts the queue impl poll operation and calls the interceptor callback") {
      verifyZeroInteractions(queueImplCallback)
      verify(interceptorCallback).invoke(any(), any(), any())
    }
  }
})
