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

package com.netflix.spinnaker.q.metrics

import com.netflix.spinnaker.assertj.softly
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.time.MutableClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

/**
 * An compatibility test for implementations of [MonitorableQueue].
 */
abstract class MonitorableQueueTest<out Q : MonitorableQueue>(
  createQueue: (Clock, DeadMessageCallback, EventPublisher?) -> Q,
  triggerRedeliveryCheck: Q.() -> Unit,
  shutdownCallback: (() -> Unit)? = null
) : Spek({

  var queue: Q? = null
  val clock = MutableClock()
  val deadMessageHandler: DeadMessageCallback = mock()
  val publisher: EventPublisher = mock()

  fun startQueue() {
    queue = createQueue(clock, deadMessageHandler, publisher)
  }

  fun resetMocks() = reset(deadMessageHandler, publisher)

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
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(0)
          assertThat(ready).isEqualTo(0)
          assertThat(unacked).isEqualTo(0)
          assertThat(orphaned).isEqualTo(0)
        }
      }
    }

    it("reports no matching message exists") {
      with(queue!!) {
        assertThat(containsMessage { it is TestMessage }).isFalse()
      }
    }
  }

  describe("pushing a message") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    on("pushing a message") {
      queue!!.push(TestMessage("a"))
    }

    it("fires an event to report the push") {
      verify(publisher).publishEvent(isA<MessagePushed>())
    }

    it("reports the updated queue depth") {
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(1)
          assertThat(unacked).isEqualTo(0)
          assertThat(ready).isEqualTo(1)
          assertThat(orphaned).isEqualTo(0)
        }
      }
    }

    it("reports a matching message exists") {
      with(queue!!) {
        assertThat(containsMessage { it is TestMessage && it.payload == "a" }).isTrue()
      }
    }
  }

  describe("pushing a duplicate message") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    beforeGroup {
      queue!!.push(TestMessage("a"))
    }

    on("pushing a duplicate message") {
      queue!!.push(TestMessage("a"))
    }

    it("fires an event to report the push") {

      /* This test previously verified publication of a MessageDuplicate event
       * but it is now optional for MonitorableQueue implementations.
       */
      verify(publisher, times(2)).publishEvent(any())
    }

    it("reports an unchanged queue depth") {
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(1)
          assertThat(unacked).isEqualTo(0)
          assertThat(ready).isEqualTo(1)
        }
      }
    }
  }

  describe("pushing a message with a delay") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    on("pushing a message with a delay") {
      queue!!.push(TestMessage("a"), Duration.ofMinutes(1))
    }

    it("fires an event to report the push") {
      verify(publisher).publishEvent(isA<MessagePushed>())
    }

    it("reports the updated queue depth") {
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(1)
          assertThat(unacked).isEqualTo(0)
          assertThat(ready).isEqualTo(0)
          assertThat(orphaned).isEqualTo(0)
        }
      }
    }
  }

  describe("in process messages") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    beforeGroup {
      queue!!.push(TestMessage("a"), Duration.ofSeconds(-1))
    }

    on("processing the message") {
      queue!!.poll { _, _ -> }
    }

    it("fires an event to report the poll") {
      verify(publisher).publishEvent(isA<QueuePolled>())
    }

    it("fires an event to report the message is being processed") {
      argumentCaptor<QueueEvent>().apply {
        verify(publisher, atLeastOnce()).publishEvent(capture())
        assertThat(allValues).anyMatch { it is MessageProcessing }
        allValues
          .first { it is MessageProcessing }
          .let { event ->
            if (event is MessageProcessing) {
              assertThat(event.payload).isEqualTo(TestMessage("a"))
              // Redis queues only store millisecond precision (in AbstractRedisQueue#score)
              // In Java 9+, we should instead use event.lag.truncatedTo(MILLIS)
              assertThat(event.lag.toMillis()).isEqualTo(Duration.ofSeconds(1).toMillis())
            }
          }
      }
    }

    it("reports unacknowledged message depth") {
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(0)
          assertThat(unacked).isEqualTo(1)
          assertThat(ready).isEqualTo(0)
          assertThat(orphaned).isEqualTo(0)
        }
      }
    }
  }

  describe("acknowledged messages") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    beforeGroup {
      queue!!.push(TestMessage("a"))
    }

    on("successfully processing a message") {
      queue!!.poll { _, ack ->
        ack.invoke()
      }
    }

    it("fires an event to report the poll") {
      verify(publisher).publishEvent(isA<MessageAcknowledged>())
    }

    it("reports an empty queue") {
      with(queue!!.readState()) {
        softly {
          assertThat(depth).isEqualTo(0)
          assertThat(unacked).isEqualTo(0)
          assertThat(ready).isEqualTo(0)
        }
      }
    }
  }

  describe("checking redelivery") {
    given("no messages need to be retried") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(TestMessage("a"))
        queue!!.poll { _, ack ->
          ack.invoke()
        }
      }

      on("checking for unacknowledged messages") {
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
      }

      it("fires an event") {
        verify(publisher).publishEvent(isA<RetryPolled>())
      }
    }

    given("a message needs to be redelivered") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(TestMessage("a"))
        queue!!.poll { _, _ -> }
      }

      on("checking for unacknowledged messages") {
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
        clock.incrementBy(queue!!.ackTimeout)
      }

      it("fires an event indicating the message is being retried") {
        verify(publisher).publishEvent(isA<MessageRetried>())
      }

      it("reports the depth with the message re-queued") {
        with(queue!!.readState()) {
          softly {
            assertThat(depth).isEqualTo(1)
            assertThat(unacked).isEqualTo(0)
            assertThat(ready).isEqualTo(1)
          }
        }
      }
    }

    given("a message needs to be redelivered but another withAttribute was already pushed") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        with(queue!!) {
          push(TestMessage("a"))
          poll { message, _ ->
            push(message)
          }
        }
      }

      on("checking for unacknowledged messages") {
        resetMocks()
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
        clock.incrementBy(queue!!.ackTimeout)
      }

      it("fires an event indicating the message is a duplicate") {
        /* This should see one of either (MessageDuplicate, MessagePushed) and
         * a RetryPolled event.
         */
        verify(publisher, times(2)).publishEvent(any())
      }

      it("reports the depth without the message re-queued") {
        with(queue!!.readState()) {
          softly {
            assertThat(depth).isEqualTo(1)
            assertThat(unacked).isEqualTo(0)
            assertThat(ready).isEqualTo(1)
          }
        }
      }
    }

    given("a message needs to be dead lettered") {
      beforeGroup(::startQueue)
      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      beforeGroup {
        queue!!.push(TestMessage("a"))
      }

      on("failing to acknowledge the message ${Queue.maxRetries} times") {
        (1..Queue.maxRetries).forEach {
          queue!!.poll { _, _ -> }
          clock.incrementBy(queue!!.ackTimeout)
          triggerRedeliveryCheck.invoke(queue!!)
          clock.incrementBy(queue!!.ackTimeout)
        }
      }

      it("fires events indicating the message was retried") {
        verify(publisher, times(Queue.maxRetries - 1)).publishEvent(isA<MessageRetried>())
      }

      it("fires an event indicating the message is being dead lettered") {
        verify(publisher).publishEvent(isA<MessageDead>())
      }

      it("reports the depth without the message re-queued") {
        with(queue!!.readState()) {
          softly {
            assertThat(depth).isEqualTo(0)
            assertThat(unacked).isEqualTo(0)
            assertThat(ready).isEqualTo(0)
            assertThat(orphaned).isEqualTo(0)
          }
        }
      }
    }
  }
})
