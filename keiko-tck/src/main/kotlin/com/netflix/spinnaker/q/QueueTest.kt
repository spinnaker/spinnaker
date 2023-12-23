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

package com.netflix.spinnaker.q

import com.netflix.spinnaker.q.Queue.Companion.maxRetries
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.time.MutableClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.internal.verification.Times

abstract class QueueTest<out Q : Queue>(
  createQueue: (Clock, DeadMessageCallback) -> Q,
  shutdownCallback: (() -> Unit)? = null
) : Spek({

  var queue: Q? = null
  val callback: QueueCallback = mock()
  val deadLetterCallback: DeadMessageCallback = mock()
  val clock = MutableClock()

  fun resetMocks() = reset(callback, deadLetterCallback)

  fun stopQueue() {
    queue?.let { q ->
      if (q is Closeable) {
        q.close()
      }
    }
    shutdownCallback?.invoke()
  }

  describe("polling the queue") {
    given("there are no messages") {
      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue") {
        queue!!.poll(callback)
      }

      it("does not invoke the callback") {
        verifyNoMoreInteractions(callback)
      }
    }

    given("there is a single message") {
      val message = TestMessage("a")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue") {
        queue!!.poll(callback)
      }

      it("passes the queued message to the callback") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("there are multiple messages") {
      val message1 = TestMessage("a")
      val message2 = TestMessage("b")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback).apply {
          push(message1)
          clock.incrementBy(Duration.ofSeconds(1))
          push(message2)
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue twice") {
        with(queue!!) {
          poll(callback)
          poll(callback)
        }
      }

      it("passes the messages to the callback in the order they were queued") {
        verify(callback).invoke(eq(message1), any())
        verify(callback).invoke(eq(message2), any())
      }
    }

    given("there is a delayed message") {
      val delay = Duration.ofHours(1)

      and("its delay has not expired") {
        val message = TestMessage("a")

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback)
          queue!!.push(message, delay)
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue") {
          queue!!.poll(callback)
        }

        it("does not invoke the callback") {
          verifyNoMoreInteractions(callback)
        }
      }

      and("its delay has expired") {
        val message = TestMessage("a")

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback)
          queue!!.push(message, delay)
          clock.incrementBy(delay)
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue") {
          queue!!.poll(callback)
        }

        it("passes the message to the callback") {
          verify(callback).invoke(eq(message), any())
        }
      }
    }
  }

  describe("message redelivery") {
    given("a message was acknowledged") {
      val message = TestMessage("a")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        with(queue!!) {
          push(message)
          poll { _, ack ->
            ack()
          }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue after the message acknowledgment has timed out") {
        with(queue!!) {
          clock.incrementBy(ackTimeout)
          retry()
          poll(callback)
        }
      }

      it("does not retry the message") {
        verifyNoMoreInteractions(callback)
      }
    }

    given("a message was not acknowledged") {
      val message = TestMessage("a")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        with(queue!!) {
          push(message)
          poll { _, _ -> }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue after the message acknowledgment has timed out") {
        with(queue!!) {
          clock.incrementBy(ackTimeout)
          retry()
          clock.incrementBy(ackTimeout)
          poll(callback)
        }
      }

      it("retries the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("a message with an ack override was not acknowledged") {
      val ackTimeoutOverride = Duration.ofMinutes(5)
      val message = TestMessage("a", ackTimeoutOverride.toMillis())

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        with(queue!!) {
          push(message)
          poll { _, _ -> }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue after the message acknowledgment default has timed out") {
        with(queue!!) {
          clock.incrementBy(ackTimeout)
          retry()
          poll(callback)
        }
      }

      it("does not retry the message") {
        verifyNoMoreInteractions(callback)
      }

      on("polling the queue after the message acknowledgment override has timed out") {
        with(queue!!) {
          clock.incrementBy(ackTimeoutOverride)
          retry()
          clock.incrementBy(ackTimeout)
          poll(callback)
        }
      }

      it("retries the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("a message was not acknowledged more than once") {
      val message = TestMessage("a")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        with(queue!!) {
          push(message)
          repeat(2) {
            poll { _, _ -> }
            clock.incrementBy(ackTimeout)
            retry()
          }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue again") {
        with(queue!!) {
          poll(callback)
        }
      }

      it("retries the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("a message was not acknowledged more than $maxRetries times") {
      val message = TestMessage("a")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        with(queue!!) {
          push(message)
          repeat(maxRetries) {
            poll { _, _ -> }
            clock.incrementBy(ackTimeout)
            retry()
            clock.incrementBy(ackTimeout)
          }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue again") {
        with(queue!!) {
          clock.incrementBy(ackTimeout)
          poll(callback)
        }
      }

      it("stops retrying the message") {
        verifyNoMoreInteractions(callback)
      }

      it("passes the failed message to the dead letter handler") {
        verify(deadLetterCallback).invoke(queue!!, message)
      }

      and("the message has been dead-lettered") {
        on("the next time retry checks happen") {
          with(queue!!) {
            retry()
            poll(callback)
          }
        }

        it("it does not get redelivered again") {
          verifyNoMoreInteractions(callback)
        }

        it("no longer gets sent to the dead letter handler") {
          verify(deadLetterCallback).invoke(queue!!, message)
        }
      }
    }
  }

  describe("message hashing") {
    given("a message was pushed") {
      val message = TestMessage("a")

      and("a duplicate is pushed with a newer delivery time") {
        val delay = Duration.ofHours(1)

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message, delay)
            push(message.copy())
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue") {
          queue!!.poll(callback)
        }

        it("delivers the message immediately and only once") {
          verify(callback).invoke(eq(message), any())
        }

        it("does not hold on to the first message") {
          clock.incrementBy(delay)
          queue!!.poll(callback)
          verifyNoMoreInteractions(callback)
        }
      }

      and("the message delivery time is updated") {
        val delay = Duration.ofHours(1)

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message, delay)
            reschedule(message, ZERO)
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue") {
          queue!!.poll(callback)
        }

        it("delivers the message immediately and only once") {
          verify(callback).invoke(eq(message), any())
        }

        it("does not deliver again") {
          verifyNoMoreInteractions(callback)
        }
      }

      and("the delivery time for a message that isn't on the queue isn't updated") {
        val message2 = message.copy(payload = "b")

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            reschedule(message2, ZERO)
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue") {
          queue!!.poll(callback)
        }

        it("there are no messages on the queue") {
          verifyNoMoreInteractions(callback)
        }
      }

      and("a different message is pushed before acknowledging the first") {
        val newMessage = message.copy(payload = "b")

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            push(newMessage)
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue more than once") {
          queue!!.poll(callback)
          queue!!.poll(callback)
        }

        it("enqueued the new message") {
          verify(callback).invoke(eq(message), any())
          verify(callback).invoke(eq(newMessage), any())
        }
      }

      and("another identical message is pushed before acknowledging the first") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            push(message.copy())
            poll { _, ack ->
              ack()
            }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue again") {
          queue!!.poll(callback)
        }

        it("did not enqueue the duplicate message") {
          verifyNoMoreInteractions(callback)
        }
      }

      and("another identical message is pushed after reading but before acknowledging the first") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, ack ->
              push(message.copy())
              ack()
            }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue again") {
          queue!!.poll(callback)
        }

        it("enqueued the second message") {
          verify(callback).invoke(eq(message), any())
        }
      }

      and("an identical message is pushed and polled after the first is read but unacked") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, ack ->
              push(message.copy())
              poll { _, ack2 ->
                ack2()
              }
              ack()
            }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue again") {
          queue!!.poll(callback)
        }

        it("the second message is still on the queue") {
          verify(callback, Times(1)).invoke(eq(message), any())
        }
      }

      and("another identical message is pushed with a delay and the first is never acknowledged") {
        val delay = Duration.ofHours(1)

        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, _ ->
              push(message.copy(), delay)
            }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue again after the first message times out") {
          with(queue!!) {
            clock.incrementBy(ackTimeout)
            retry()
            clock.incrementBy(ackTimeout)
            poll(callback)
          }
        }

        it("re-queued the message for immediate delivery") {
          verify(callback).invoke(eq(message), any())
        }

        it("discarded the delayed message") {
          clock.incrementBy(delay)
          queue!!.poll(callback)
          verifyNoMoreInteractions(callback)
        }
      }

      and("another identical message is pushed after acknowledging the first") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, ack ->
              ack()
            }
            push(message.copy())
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("polling the queue again") {
          queue!!.poll(callback)
        }

        it("enqueued the second message") {
          verify(callback).invoke(eq(message), any())
        }
      }

      and("another identical message is pushed and the first is never acknowledged") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, _ ->
              push(message.copy())
            }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("after the first message's acknowledgment times out") {
          with(queue!!) {
            clock.incrementBy(ackTimeout)
            retry()
            clock.incrementBy(ackTimeout)
            poll(callback)
            poll(callback)
          }
        }

        it("does not re-deliver the first message") {
          verify(callback).invoke(eq(message), any())
          verifyNoMoreInteractions(callback)
        }
      }

      /* ktlint-disable max-line-length */
      and("the first message is never acknowledged, gets re-delivered then another identical message is pushed") {
        /* ktlint-enable max-line-length */
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            push(message)
            poll { _, _ -> }
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("after the first message's acknowledgment times out") {
          with(queue!!) {
            clock.incrementBy(ackTimeout)
            retry()
            push(message.copy())
            poll(callback)
            poll(callback)
          }
        }

        it("did not enqueue the new duplicate message") {
          verify(callback).invoke(eq(message), any())
          verifyNoMoreInteractions(callback)
        }
      }

      and("an ensured message is not on the queue") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            ensure(message, ZERO)
          }
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        on("queue poll") {
          with(queue!!) {
            poll(callback)
          }
        }

        it("enqueues the message only once") {
          verify(callback).invoke(eq(message), any())
          verifyNoMoreInteractions(callback)
        }
      }

      and("an ensured message is already on the queue and ensure is called again") {
        beforeGroup {
          queue = createQueue(clock, deadLetterCallback).apply {
            ensure(message, ZERO)
            ensure(message, ZERO)
            poll(callback)
          }

          afterGroup(::stopQueue)
          afterGroup(::resetMocks)

          on("queue poll") {
            with(queue!!) {
              poll(callback)
            }
          }

          it("the message was not duplicated into the queue") {
            verifyNoMoreInteractions(callback)
          }
        }
      }
    }
  }
})
