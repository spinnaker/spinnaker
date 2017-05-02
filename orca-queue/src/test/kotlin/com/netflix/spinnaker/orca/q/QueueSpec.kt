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
import com.netflix.spinnaker.orca.q.Queue.Companion.maxRedeliveries
import com.netflix.spinnaker.orca.time.MutableClock
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.Closeable
import java.time.Duration
import java.time.Instant

abstract class QueueSpec<out Q : Queue>(
  createQueue: ((Queue, Message) -> Unit) -> Q,
  triggerRedeliveryCheck: Q.() -> Unit,
  shutdownCallback: (() -> Unit)? = null
) : Spek({

  var queue: Q? = null
  val callback: QueueCallback = mock()
  val deadLetterCallback: (Queue, Message) -> Unit = mock()

  fun resetMocks() = reset(callback)

  fun stopQueue() {
    queue?.let { q ->
      if (q is Closeable) {
        q.close()
      }
    }
    shutdownCallback?.invoke()
  }

  describe("polling the queue") {
    context("there are no messages") {
      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled") {
        queue!!.poll(callback)
      }

      it("does not invoke the callback") {
        verifyZeroInteractions(callback)
      }
    }

    context("there is a single message") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled") {
        queue!!.poll(callback)
      }

      it("passes the queued message to the callback") {
        verify(callback).invoke(eq(message), any())
      }
    }

    context("there are multiple messages") {
      val message1 = StartExecution(Pipeline::class.java, "1", "foo")
      val message2 = StartExecution(Pipeline::class.java, "2", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback).apply {
          push(message1)
          clock.incrementBy(Duration.ofSeconds(1))
          push(message2)
        }
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled twice") {
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

    context("there is a delayed message") {
      val delay = Duration.ofHours(1)

      context("whose delay has not expired") {
        val message = StartExecution(Pipeline::class.java, "1", "foo")

        beforeGroup {
          queue = createQueue.invoke(deadLetterCallback)
          queue!!.push(message, delay)
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        action("the queue is polled") {
          queue!!.poll(callback)
        }

        it("does not invoke the callback") {
          verifyZeroInteractions(callback)
        }
      }

      context("whose delay has expired") {
        val message = StartExecution(Pipeline::class.java, "1", "foo")

        beforeGroup {
          queue = createQueue.invoke(deadLetterCallback)
          queue!!.push(message, delay)
          clock.incrementBy(delay)
        }

        afterGroup(::stopQueue)
        afterGroup(::resetMocks)

        action("the queue is polled") {
          queue!!.poll(callback)
        }

        it("passes the message to the callback") {
          verify(callback).invoke(eq(message), any())
        }
      }
    }
  }

  describe("message redelivery") {
    context("a message is acknowledged") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled and the message is acknowledged") {
        queue!!.apply {
          poll { _, ack ->
            ack.invoke()
          }
          clock.incrementBy(ackTimeout)
          triggerRedeliveryCheck()
          poll(callback)
        }
      }

      it("does not re-deliver the message") {
        verifyZeroInteractions(callback)
      }
    }

    context("a message is not acknowledged") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled then the message is not acknowledged") {
        queue!!.apply {
          poll { _, _ -> }
          clock.incrementBy(ackTimeout)
          triggerRedeliveryCheck()
          poll(callback)
        }
      }

      it("re-delivers the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    context("a message is not acknowledged more than once") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled and the message is not acknowledged") {
        queue!!.apply {
          repeat(2) {
            poll { _, _ -> }
            clock.incrementBy(ackTimeout)
            triggerRedeliveryCheck()
          }
          poll(callback)
        }
      }

      it("re-delivers the message") {
        verify(callback).invoke(eq(message), any())
      }
    }

    context("a message is not acknowledged more than $maxRedeliveries times") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        queue = createQueue.invoke(deadLetterCallback)
        queue!!.push(message)
      }

      afterGroup(::stopQueue)
      afterGroup(::resetMocks)

      action("the queue is polled and the message is not acknowledged") {
        queue!!.apply {
          repeat(maxRedeliveries) {
            poll { _, _ -> }
            clock.incrementBy(ackTimeout)
            triggerRedeliveryCheck()
          }
          poll(callback)
        }
      }

      it("stops re-delivering the message") {
        verifyZeroInteractions(callback)
      }

      it("passes the failed message to the dead letter handler") {
        verify(deadLetterCallback).invoke(queue!!, message)
      }

      context("once the message has been dead-lettered") {
        action("the next time re-delivery checks happen") {
          queue!!.apply {
            triggerRedeliveryCheck()
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
}) {
  companion object {
    val clock = MutableClock(Instant.now())
  }
}
