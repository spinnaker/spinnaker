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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object StartExecutionHandlerTest : SubjectSpek<StartExecutionHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  subject(GROUP) {
    StartExecutionHandler(queue, repository, publisher, clock)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("starting an execution") {
    context("with a single initial stage") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
      }
      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the execution as running") {
        verify(repository).updateStatus(message.executionId, ExecutionStatus.RUNNING)
      }

      it("starts the first stage") {
        verify(queue).push(StartStage(pipeline.stages.first()))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionStarted> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
        })
      }
    }

    context("that was previously canceled and status is CANCELED") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        status = ExecutionStatus.CANCELED
      }

      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(ExecutionStatus.CANCELED)
        })
      }

      it("pushes no messages to the queue") {
        verifyNoMoreInteractions(queue)
      }
    }

    context("that was previously canceled and status is NOT_STARTED") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        isCanceled = true
      }

      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(ExecutionStatus.NOT_STARTED)
        })
      }

      it("pushes no messages to the queue") {
        verifyNoMoreInteractions(queue)
      }
    }

    context("with multiple initial stages") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        stage {
          type = singleTaskStage.type
        }
      }
      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("starts all the initial stages") {
        argumentCaptor<StartStage>().apply {
          verify(queue, times(2)).push(capture())
          assertThat(allValues.map { it.stageId }.toSet()).isEqualTo(pipeline.stages.map { it.id }.toSet())
        }
      }
    }

    context("with no initial stages") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
          requisiteStageRefIds = listOf("1")
        }
        stage {
          type = singleTaskStage.type
          requisiteStageRefIds = listOf("1")
        }
      }
      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the execution as TERMINAL") {
        verify(repository, times(1)).updateStatus(pipeline.id, ExecutionStatus.TERMINAL)
      }

      it("publishes an event with TERMINAL status") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(ExecutionStatus.TERMINAL)
        })
      }
    }

    context("with a start time after ttl") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        startTimeTtl = clock.instant().minusSeconds(30)
      }
      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives the message") {
        subject.handle(message)
      }

      it("cancels the execution") {
        verify(queue).push(CancelExecution(
          pipeline,
          "spinnaker",
          "Could not begin execution before start time TTL"
        ))
      }
    }
  }
})
