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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.DeadMessageCallback
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.time.MutableClock
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.Closeable
import java.time.Clock

abstract class MonitoredQueueSpec<out Q : MonitoredQueue>(
  createQueue: (Clock, DeadMessageCallback, Registry) -> Q,
  triggerRedeliveryCheck: Q.() -> Unit,
  shutdownCallback: (() -> Unit)? = null
) : Spek({

  var queue: Q? = null
  val clock = MutableClock()
  val deadMessageHandler: DeadMessageCallback = mock()

  val pushCounter: Counter = mock()
  val ackCounter: Counter = mock()
  val retryCounter: Counter = mock()
  val deadMessageCounter: Counter = mock()

  val registry: Registry = mock {
    on { counter("queue.pushed.messages") } doReturn pushCounter
    on { counter("queue.acknowledged.messages") } doReturn ackCounter
    on { counter("queue.retried.messages") } doReturn retryCounter
    on { counter("queue.dead.messages") } doReturn deadMessageCounter
  }

  fun startQueue() {
    queue = createQueue(clock, deadMessageHandler, registry)
  }

  fun resetMocks() = reset(deadMessageHandler, pushCounter, ackCounter, retryCounter)

  fun stopQueue() {
    queue?.let { q ->
      if (q is Closeable) {
        q.close()
      }
    }
    shutdownCallback?.invoke()
  }

  describe("an empty queue") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports empty") {
      queue!!.apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 0
        lastQueuePoll.get() shouldEqual null
      }
    }
  }

  describe("pushing a message") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    on("pushing a message") {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
    }

    it("increments a counter") {
      verify(pushCounter).increment()
      verifyNoMoreInteractions(pushCounter, ackCounter, retryCounter)
    }

    it("reports the queue depth") {
      queue!!.apply {
        queueDepth shouldEqual 1
        unackedDepth shouldEqual 0
        lastQueuePoll.get() shouldEqual null
      }
    }
  }

  describe("in process messages") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
    }

    on("processing the message") {
      queue!!.poll { _, _ -> }
    }

    it("reports unacknowledged message depth") {
      queue!!.apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 1
        lastQueuePoll.get() shouldEqual clock.instant()
      }
    }
  }

  describe("acknowledged messages") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
    }

    on("successfully processing a message") {
      queue!!.poll { _, ack ->
        ack.invoke()
      }
    }

    it("increments a counter") {
      verify(ackCounter).increment()
    }

    it("reports an empty queue") {
      queue!!.apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 0
        lastQueuePoll.get() shouldEqual clock.instant()
      }
    }
  }

  describe("checking redelivery") {
    given("no messages need to be retried") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
        queue!!.poll { _, ack ->
          ack.invoke()
        }
      }

      on("checking for unacknowledged messages") {
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
      }

      it("does not increment the redelivery count") {
        verifyZeroInteractions(retryCounter)
      }

      it("reports the time of the last redelivery check") {
        queue!!.lastRetryPoll.get() shouldEqual clock.instant()
      }
    }

    given("a message needs to be redelivered") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
        queue!!.poll { _, _ -> }
      }

      on("checking for unacknowledged messages") {
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
      }

      it("reports the depth with the message re-queued") {
        queue!!.apply {
          queueDepth shouldEqual 1
          unackedDepth shouldEqual 0
        }
      }

      it("increments the redelivery count") {
        verify(retryCounter).increment()
      }

      it("reports the time of the last redelivery check") {
        queue!!.lastRetryPoll.get() shouldEqual clock.instant()
      }
    }

    given("a message needs to be dead lettered") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      }

      on("failing to acknowledge the message ${Queue.maxRetries} times") {
        (1..Queue.maxRetries).forEach {
          queue!!.poll { _, _ -> }
          clock.incrementBy(queue!!.ackTimeout)
          triggerRedeliveryCheck.invoke(queue!!)
        }
      }

      it("reports the depth without the message re-queued") {
        queue!!.apply {
          queueDepth shouldEqual 0
          unackedDepth shouldEqual 0
        }
      }

      it("counts the redelivery attempts") {
        verify(retryCounter, times(Queue.maxRetries - 1)).increment()
      }

      it("increments the dead letter count") {
        verify(deadMessageCounter).increment()
      }
    }
  }
})
