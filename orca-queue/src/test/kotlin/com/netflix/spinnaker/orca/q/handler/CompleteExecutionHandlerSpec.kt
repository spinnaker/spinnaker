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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.stage
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.springframework.context.ApplicationEventPublisher

class CompleteExecutionHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()

  val handler = CompleteExecutionHandler(queue, repository, publisher)

  fun resetMocks() = reset(queue, repository, publisher)

  describe("when an execution completes successfully") {
    val pipeline = pipeline { }
    val message = CompleteExecution(Pipeline::class.java, pipeline.id, "foo", SUCCEEDED)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, SUCCEEDED)
    }
  }

  setOf(TERMINAL, CANCELED).forEach { status ->
    describe("when an execution fails with $status status") {
      val pipeline = pipeline()
      val message = CompleteExecution(Pipeline::class.java, pipeline.id, "foo", status)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the execution") {
        verify(repository).updateStatus(message.executionId, status)
      }
    }
  }

  setOf(SUCCEEDED, TERMINAL, CANCELED).forEach { status ->
    describe("when an execution completes with $status status") {
      val pipeline = pipeline()
      val message = CompleteExecution(Pipeline::class.java, pipeline.id, "foo", status)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          it.executionType shouldEqual pipeline.javaClass
          it.executionId shouldEqual pipeline.id
          it.status shouldEqual status
        })
      }
    }
  }

  describe("when a stage status was FAILED_CONTINUE but should fail the pipeline at the end") {
    val pipeline = pipeline {
      stage {
        type = "dummy"
        status = FAILED_CONTINUE
        context["completeOtherBranchesThenFail"] = true
      }
    }
    val message = CompleteExecution(Pipeline::class.java, pipeline.id, "foo", SUCCEEDED)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, TERMINAL)
    }
  }

  describe("when a stage status was FAILED_CONTINUE and should not fail the pipeline at the end") {
    val pipeline = pipeline {
      stage {
        type = "dummy"
        status = FAILED_CONTINUE
        context["completeOtherBranchesThenFail"] = false
      }
    }
    val message = CompleteExecution(Pipeline::class.java, pipeline.id, "foo", SUCCEEDED)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, SUCCEEDED)
    }
  }
})
