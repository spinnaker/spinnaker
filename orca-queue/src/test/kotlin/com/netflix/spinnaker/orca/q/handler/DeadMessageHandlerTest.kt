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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object DeadMessageHandlerTest : SubjectSpek<DeadMessageHandler>({

  val queue: Queue = mock()

  subject(GROUP) {
    DeadMessageHandler()
  }

  fun resetMocks() = reset(queue)

  describe("handling an execution level message") {
    val message = StartExecution(Pipeline::class.java, "1", "spinnaker")

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.handle(queue, message)
    }

    it("terminates the execution") {
      verify(queue).push(CompleteExecution(message))
    }
  }

  describe("handling a stage level message") {
    val message = StartStage(Pipeline::class.java, "1", "spinnaker", "1")

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.handle(queue, message)
    }

    it("terminates the stage") {
      verify(queue).push(CompleteStage(message, TERMINAL))
    }
  }

  describe("handling a task level message") {
    val message = RunTask(Pipeline::class.java, "1", "spinnaker", "1", "1", DummyTask::class.java)

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.handle(queue, message)
    }

    it("terminates the task") {
      verify(queue).push(CompleteTask(message, TERMINAL))
    }
  }

  describe("handling a message that was previously dead-lettered") {
    val message = CompleteExecution(Pipeline::class.java, "1", "spinnaker").apply {
      setAttribute(DeadMessageAttribute)
    }

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.handle(queue, message)
    }

    it("does nothing") {
      verifyZeroInteractions(queue)
    }
  }
})
