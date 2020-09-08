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

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.mockito.doStub
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.NoHandlerCapacity
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object QueueProcessorTest : Spek({
  describe("the queue processor") {
    val queue: Queue = mock()
    val simpleMessageHandler: MessageHandler<SimpleMessage> = mock()
    val parentMessageHandler: MessageHandler<ParentMessage> = mock()
    val ackFunction: () -> Unit = mock()
    val activator: Activator = mock()
    val publisher: EventPublisher = mock()
    val deadMessageHandler: DeadMessageCallback = mock()

    fun resetMocks() = reset(
      queue,
      simpleMessageHandler,
      parentMessageHandler,
      ackFunction,
      publisher
    )

    describe("when disabled") {
      val subject = QueueProcessor(
        queue,
        BlockingQueueExecutor(),
        emptyList(),
        listOf(activator),
        publisher,
        deadMessageHandler
      )

      afterGroup(::resetMocks)

      on("the next polling cycle") {
        subject.poll()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(queue)
      }
    }

    describe("when enabled") {
      beforeEachTest {
        whenever(activator.enabled) doReturn true
      }

      and("there is no capacity in the thread pool") {
        val executor: QueueExecutor<*> = mock()

        val subject = QueueProcessor(
          queue,
          executor,
          emptyList(),
          listOf(activator),
          publisher,
          deadMessageHandler
        )

        beforeGroup {
          whenever(executor.hasCapacity()) doReturn false
        }

        afterGroup {
          resetMocks()
          reset(executor)
        }

        action("the worker runs") {
          subject.poll()
        }

        it("does not poll the queue") {
          verifyZeroInteractions(queue)
        }

        it("fires an event") {
          verify(publisher).publishEvent(isA<NoHandlerCapacity>())
        }
      }

      and("there is capacity in the thread pool") {
        val subject = QueueProcessor(
          queue,
          BlockingQueueExecutor(),
          listOf(simpleMessageHandler, parentMessageHandler),
          listOf(activator),
          publisher,
          deadMessageHandler
        )

        describe("when a message is on the queue") {
          and("it is a supported message type") {
            val message = SimpleMessage("foo")

            beforeGroup {
              whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
              whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

              whenever(queue.poll(any())) doStub { callback: QueueCallback ->
                callback.invoke(message, ackFunction)
              }
            }

            afterGroup(::resetMocks)

            on("the next polling cycle") {
              subject.poll()
            }

            it("passes the message to the correct handler") {
              verify(simpleMessageHandler).invoke(eq(message))
            }

            it("does not invoke other handlers") {
              verify(parentMessageHandler, never()).invoke(any())
            }

            it("acknowledges the message") {
              verify(ackFunction).invoke()
            }
          }

          and("it is a subclass of a supported message type") {
            val message = ChildMessage("foo")

            beforeGroup {
              whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
              whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

              whenever(queue.poll(any())) doStub { callback: QueueCallback ->
                callback.invoke(message, ackFunction)
              }
            }

            afterGroup(::resetMocks)

            on("the next polling cycle") {
              subject.poll()
            }

            it("passes the message to the correct handler") {
              verify(parentMessageHandler).invoke(eq(message))
            }

            it("does not invoke other handlers") {
              verify(simpleMessageHandler, never()).invoke(any())
            }

            it("acknowledges the message") {
              verify(ackFunction).invoke()
            }
          }

          and("it is an unsupported message type") {
            val message = UnsupportedMessage("foo")

            beforeGroup {
              whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
              whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

              whenever(queue.poll(any())) doStub { callback: QueueCallback ->
                callback.invoke(message, ackFunction)
              }
            }

            afterGroup(::resetMocks)

            on("the next polling cycle") {
              subject.poll()
            }

            it("does not invoke any handlers") {
              verify(simpleMessageHandler, never()).invoke(any())
              verify(parentMessageHandler, never()).invoke(any())
            }

            it("does not acknowledge the message") {
              verify(ackFunction, never()).invoke()
            }

            it("sends the message to the DLQ") {
              verify(deadMessageHandler).invoke(queue, message)
            }

            it("emits an event") {
              verify(publisher).publishEvent(MessageDead)
            }
          }

          context("the handler throws an exception") {
            val message = SimpleMessage("foo")

            beforeGroup {
              whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
              whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

              whenever(queue.poll(any())) doStub { callback: QueueCallback ->
                callback.invoke(message, ackFunction)
              }

              whenever(simpleMessageHandler.invoke(any())) doThrow DummyException()
            }

            afterGroup(::resetMocks)

            on("the next polling cycle") {
              subject.poll()
            }

            it("does not acknowledge the message") {
              verify(ackFunction, never()).invoke()
            }
          }
        }
      }
    }
  }
})

@JsonTypeName("simple")
data class SimpleMessage(val payload: String) : Message()

sealed class ParentMessage : Message()

@JsonTypeName("child")
data class ChildMessage(val payload: String) : ParentMessage()

@JsonTypeName("unsupported")
data class UnsupportedMessage(val payload: String) : Message()

class BlockingThreadExecutor : Executor {

  private val delegate = Executors.newSingleThreadExecutor()

  override fun execute(command: Runnable) {
    val latch = CountDownLatch(1)
    delegate.execute {
      try {
        command.run()
      } finally {
        latch.countDown()
      }
    }
    latch.await()
  }
}

class BlockingQueueExecutor : QueueExecutor<Executor>(BlockingThreadExecutor()) {
  override fun hasCapacity() = true
  override fun availableCapacity(): Int = 1
}

class DummyException : RuntimeException("deliberate exception for test")
