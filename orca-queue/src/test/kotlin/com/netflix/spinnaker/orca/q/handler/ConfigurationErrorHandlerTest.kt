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
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object ConfigurationErrorHandlerTest : SubjectSpek<ConfigurationErrorHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    ConfigurationErrorHandler(queue, repository)
  }

  fun resetMocks() = reset(queue, repository)

  InvalidExecutionId(PIPELINE, "1", "foo").let { message ->
    describe("handing a ${message.javaClass.simpleName} event") {
      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("does not try to update the execution status") {
        verifyZeroInteractions(repository)
      }

      it("does not push any messages to the queue") {
        verifyZeroInteractions(queue)
      }
    }
  }

  setOf<ConfigurationError>(
    InvalidStageId(PIPELINE, "1", "foo", "1"),
    InvalidTaskId(PIPELINE, "1", "foo", "1", "1"),
    InvalidTaskType(PIPELINE, "1", "foo", "1", InvalidTask::class.java.name),
    NoDownstreamTasks(PIPELINE, "1", "foo", "1", "1")
  ).forEach { message ->
    describe("handing a ${message.javaClass.simpleName} event") {
      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("marks the execution as terminal") {
        verify(repository).updateStatus(PIPELINE, message.executionId, TERMINAL)
      }

      it("does not push any messages to the queue") {
        verifyZeroInteractions(queue)
      }
    }
  }
})
