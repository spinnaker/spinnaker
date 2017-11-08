/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Duration
import java.time.Instant.now

object AtlasQueueMonitorTest : SubjectSpek<AtlasQueueMonitor>({

  val queue: MonitorableQueue = mock()
  val clock = fixedClock(instant = now().minus(Duration.ofHours(1)))

  val pushCounter: Counter = mock()
  val ackCounter: Counter = mock()
  val retryCounter: Counter = mock()
  val deadCounter: Counter = mock()
  val duplicateCounter: Counter = mock()
  val lockFailedCounter: Counter = mock()

  val registry: Registry = mock {
    on { counter(eq("queue.pushed.messages"), anyVararg<String>()) } doReturn pushCounter
    on { counter("queue.acknowledged.messages") } doReturn ackCounter
    on { counter("queue.retried.messages") } doReturn retryCounter
    on { counter("queue.dead.messages") } doReturn deadCounter
    on { counter(eq("queue.duplicate.messages"), anyVararg<String>()) } doReturn duplicateCounter
    on { counter("queue.lock.failed") } doReturn lockFailedCounter
  }

  subject(GROUP) {
    AtlasQueueMonitor(queue, registry, clock)
  }

  fun resetMocks() =
    reset(queue, pushCounter, ackCounter, retryCounter, deadCounter, duplicateCounter)

  describe("default values") {
    it("reports system uptime if the queue has never been polled") {
      subject.lastQueuePoll shouldEqual clock.instant()
      subject.lastRetryPoll shouldEqual clock.instant()
    }
  }

  describe("responding to queue events") {
    describe("when the queue is polled") {
      afterGroup(::resetMocks)

      val event = QueuePolled(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("updates the last poll time") {
        subject.lastQueuePoll shouldEqual event.instant
      }
    }

    describe("when the retry queue is polled") {
      afterGroup(::resetMocks)

      val event = RetryPolled(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("updates the last poll time") {
        subject.lastRetryPoll shouldEqual event.instant
      }
    }

    describe("when a message is pushed") {
      afterGroup(::resetMocks)

      val event = MessagePushed(queue, StartExecution(PIPELINE, "1", "covfefe"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(pushCounter).increment()
      }
    }

    describe("when a message is acknowledged") {
      afterGroup(::resetMocks)

      val event = MessageAcknowledged(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(ackCounter).increment()
      }
    }

    describe("when a message is retried") {
      afterGroup(::resetMocks)

      val event = MessageRetried(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(retryCounter).increment()
      }
    }

    describe("when a message is dead") {
      afterGroup(::resetMocks)

      val event = MessageDead(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(deadCounter).increment()
      }
    }

    describe("when a duplicate message is pushed") {
      afterGroup(::resetMocks)

      val event = MessageDuplicate(queue, StartExecution(PIPELINE, "1", "covfefe"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(duplicateCounter).increment()
      }
    }

    describe("when an instance fails to lock a message") {
      afterGroup(::resetMocks)

      val event = LockFailed(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(lockFailedCounter).increment()
      }
    }
  }

  describe("checking queue state") {
    afterGroup(::resetMocks)

    val queueState = QueueState(4, 1, 2, 0)

    beforeGroup {
      whenever(queue.readState()) doReturn queueState
    }

    on("checking queue state") {
      subject.pollQueueState()
    }

    it("updates the queue state") {
      subject.lastState shouldEqual queueState
    }
  }
})
