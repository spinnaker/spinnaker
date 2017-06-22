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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration

object CompleteExecutionHandlerSpec : SubjectSpek<CompleteExecutionHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val retryDelay = Duration.ofSeconds(5)

  subject(GROUP) {
    CompleteExecutionHandler(queue, repository, publisher, retryDelayMs = retryDelay.toMillis())
  }

  fun resetMocks() = reset(queue, repository, publisher)

  setOf(SUCCEEDED, TERMINAL, CANCELED).forEach { stageStatus ->
    describe("when an execution completes and has a single stage with $stageStatus status") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          status = stageStatus
        }
      }
      val message = CompleteExecution(pipeline)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("updates the execution") {
        verify(repository).updateStatus(message.executionId, stageStatus)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          it.executionType shouldEqual pipeline.javaClass
          it.executionId shouldEqual pipeline.id
          it.status shouldEqual stageStatus
        })
      }

      it("does not queue any other commands") {
        verifyZeroInteractions(queue)
      }
    }
  }

  setOf(SUCCEEDED, STOPPED, FAILED_CONTINUE, SKIPPED).forEach { stageStatus ->
    describe("an execution appears to complete with one branch $stageStatus but other branches are still running") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          status = stageStatus
        }
        stage {
          refId = "2"
          status = RUNNING
        }
      }
      val message = CompleteExecution(pipeline)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("waits for the other branch(es)") {
        verify(repository, never()).updateStatus(eq(pipeline.id), any())
      }

      it("does not publish any events") {
        verifyZeroInteractions(publisher)
      }

      it("re-queues the message for later evaluation") {
        verify(queue).push(message, retryDelay)
        verifyNoMoreInteractions(queue)
      }
    }
  }

  setOf(TERMINAL, CANCELED).forEach { stageStatus ->
    describe("a stage signals branch completion with $stageStatus but other branches are still running") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          status = stageStatus
        }
        stage {
          refId = "2"
          status = RUNNING
        }
        stage {
          refId = "3"
          status = RUNNING
        }
      }
      val message = CompleteExecution(pipeline)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("updates the pipeline status") {
        verify(repository).updateStatus(pipeline.id, stageStatus)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          it.executionType shouldEqual pipeline.javaClass
          it.executionId shouldEqual pipeline.id
          it.status shouldEqual stageStatus
        })
      }

      it("cancels other stages") {
        verify(queue).push(CancelStage(pipeline.stageByRef("2")))
        verify(queue).push(CancelStage(pipeline.stageByRef("3")))
        verifyNoMoreInteractions(queue)
      }
    }
  }

  describe("when a stage status was STOPPED but should fail the pipeline at the end") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1a"
        status = STOPPED
        context["completeOtherBranchesThenFail"] = true
      }
      stage {
        refId = "1b"
        requisiteStageRefIds = setOf("1a")
        status = NOT_STARTED
      }
      stage {
        refId = "2"
        status = SUCCEEDED
      }
    }
    val message = CompleteExecution(pipeline)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    on("receiving $message") {
      subject.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, TERMINAL)
    }

    it("publishes an event") {
      verify(publisher).publishEvent(check<ExecutionComplete> {
        it.executionType shouldEqual pipeline.javaClass
        it.executionId shouldEqual pipeline.id
        it.status shouldEqual TERMINAL
      })
    }

    it("does not queue any other commands") {
      verifyZeroInteractions(queue)
    }
  }

  describe("when a stage status was STOPPED and should not fail the pipeline at the end") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1a"
        status = STOPPED
        context["completeOtherBranchesThenFail"] = false
      }
      stage {
        refId = "1b"
        requisiteStageRefIds = setOf("1a")
        status = NOT_STARTED
      }
      stage {
        refId = "2"
        status = SUCCEEDED
      }
    }
    val message = CompleteExecution(pipeline)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    on("receiving $message") {
      subject.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, SUCCEEDED)
    }

    it("publishes an event") {
      verify(publisher).publishEvent(check<ExecutionComplete> {
        it.executionType shouldEqual pipeline.javaClass
        it.executionId shouldEqual pipeline.id
        it.status shouldEqual SUCCEEDED
      })
    }

    it("does not queue any other commands") {
      verifyZeroInteractions(queue)
    }
  }

  describe("when a branch is stopped and nothing downstream has started yet") {
    val pipeline = pipeline {
      application = "covfefe"
      stage {
        refId = "1a"
        status = STOPPED
        context["completeOtherBranchesThenFail"] = false
      }
      stage {
        refId = "2a"
        status = SUCCEEDED
        context["completeOtherBranchesThenFail"] = false
      }
      stage {
        refId = "1b"
        requisiteStageRefIds = setOf("1a")
        status = NOT_STARTED
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = setOf("2a")
        status = NOT_STARTED
      }
    }
    val message = CompleteExecution(pipeline)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    on("receiving $message") {
      subject.handle(message)
    }

    it("does not complete the execution") {
      verify(repository, never()).updateStatus(any(), any())
    }

    it("publishes no events") {
      verifyZeroInteractions(publisher)
    }

    it("re-queues the message") {
      verify(queue).push(message, retryDelay)
    }
  }
})
