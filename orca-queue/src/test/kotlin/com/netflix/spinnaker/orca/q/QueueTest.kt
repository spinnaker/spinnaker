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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.Queue.Companion.maxRetries
import com.netflix.spinnaker.orca.time.MutableClock
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.Closeable
import java.time.Clock
import java.time.Duration

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
        verifyZeroInteractions(callback)
      }
    }

    given("there is a single message") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

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
      val message1 = StartExecution(Pipeline::class.java, "1", "foo")
      val message2 = StartExecution(Pipeline::class.java, "2", "foo")

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
        queue!!.apply {
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
        val message = StartExecution(Pipeline::class.java, "1", "foo")

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
          verifyZeroInteractions(callback)
        }
      }

      and("its delay has expired") {
        val message = StartExecution(Pipeline::class.java, "1", "foo")

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
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        queue!!.apply {
          push(message)
          poll { _, ack ->
            ack()
          }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue after the message acknowledgment has timed out") {
        queue!!.apply {
          clock.incrementBy(ackTimeout)
          retry()
          poll(callback)
        }
      }

      it("does not retry the message") {
        verifyZeroInteractions(callback)
      }
    }

    given("a message was not acknowledged") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        queue!!.apply {
          push(message)
          poll { _, _ -> }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue after the message acknowledgment has timed out") {
        queue!!.apply {
          clock.incrementBy(ackTimeout)
          retry()
          poll(callback)
        }
      }

      it("retries the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("a message was not acknowledged more than once") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        queue!!.apply {
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
        queue!!.apply {
          poll(callback)
        }
      }

      it("retries the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    given("a message was not acknowledged more than $maxRetries times") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue(clock, deadLetterCallback)
        queue!!.apply {
          push(message)
          repeat(maxRetries) {
            poll { _, _ -> }
            clock.incrementBy(ackTimeout)
            retry()
          }
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      on("polling the queue again") {
        queue!!.apply {
          poll(callback)
        }
      }

      it("stops retrying the message") {
        verifyZeroInteractions(callback)
      }

      it("passes the failed message to the dead letter handler") {
        verify(deadLetterCallback).invoke(queue!!, message)
      }

      and("the message has been dead-lettered") {
        on("the next time retry checks happen") {
          queue!!.apply {
            retry()
            poll(callback)
          }
        }

        it("it does not get redelivered again") {
          verifyZeroInteractions(callback)
        }

        it("no longer gets sent to the dead letter handler") {
          verify(deadLetterCallback).invoke(queue!!, message)
        }
      }
    }
  }

  describe("message hashing") {
    given("a message was pushed") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      and("a different message is pushed before acknowledging the first") {
        val newMessage = message.copy(executionId = "2")

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
          verifyZeroInteractions(callback)
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
            poll(callback)
            poll(callback)
          }
        }

        it("does not re-deliver the first message") {
          verify(callback).invoke(eq(message), any())
          verifyNoMoreInteractions(callback)
        }
      }

      and("the first message is never acknowledged, gets re-delivered then another identical message is pushed") {
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
    }
  }
})
