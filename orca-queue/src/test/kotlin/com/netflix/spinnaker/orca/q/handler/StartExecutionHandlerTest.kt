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
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher
import rx.Observable.just
import java.util.*

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
    given("a pipeline with a single initial stage") {
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

      on("receiving a message") {
        subject.handle(message)
      }

      it("marks the execution as running") {
        verify(repository).updateStatus(message.executionId, RUNNING)
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

    given("a pipeline that was previously canceled and status is CANCELED") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        status = CANCELED
      }

      val message = StartExecution(pipeline)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(CANCELED)
        })
      }

      it("pushes no messages to the queue") {
        verifyNoMoreInteractions(queue)
      }
    }

    given("a pipeline that was previously canceled and status is NOT_STARTED") {
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

      on("receiving a message") {
        subject.handle(message)
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(NOT_STARTED)
        })
      }

      it("pushes no messages to the queue") {
        verifyNoMoreInteractions(queue)
      }
    }

    given("a pipeline with multiple initial stages") {
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

      on("receiving a message") {
        subject.handle(message)
      }

      it("starts all the initial stages") {
        argumentCaptor<StartStage>().apply {
          verify(queue, times(2)).push(capture())
          assertThat(allValues)
            .extracting("stageId")
            .containsExactlyInAnyOrderElementsOf(pipeline.stages.map { it.id })
        }
      }
    }

    given("a pipeline with no initial stages") {
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

      on("receiving a message") {
        subject.handle(message)
      }

      it("marks the execution as TERMINAL") {
        verify(repository, times(1)).updateStatus(pipeline.id, TERMINAL)
      }

      it("publishes an event with TERMINAL status") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.status).isEqualTo(TERMINAL)
        })
      }
    }

    given("a start time after ttl") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
        startTimeExpiry = clock.instant().minusSeconds(30).toEpochMilli()
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
          "Could not begin execution before start time expiry"
        ))
      }
    }

    given("a pipeline with another instance already running") {
      val configId = UUID.randomUUID().toString()
      val runningPipeline = pipeline {
        pipelineConfigId = configId
        isLimitConcurrent = true
        status = RUNNING
        stage {
          type = singleTaskStage.type
          status = RUNNING
        }
      }
      val pipeline = pipeline {
        pipelineConfigId = configId
        isLimitConcurrent = true
        stage {
          type = singleTaskStage.type
        }
      }
      val message = StartExecution(pipeline.type, pipeline.id, pipeline.application)

      and("the pipeline should not run multiple executions concurrently") {
        beforeGroup {
          pipeline.isLimitConcurrent = true
          runningPipeline.isLimitConcurrent = true

          whenever(
            repository
              .retrievePipelinesForPipelineConfigId(configId, ExecutionCriteria().setLimit(1).setStatuses(RUNNING))
          ) doReturn just(runningPipeline)
          whenever(
            repository.retrieve(message.executionType, message.executionId)
          ) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not start the new pipeline") {
          verify(repository, never()).updateStatus(message.executionId, RUNNING)
          verify(queue, never()).push(isA<StartStage>())
        }

        it("does not push any messages to the queue") {
          verifyNoMoreInteractions(queue)
        }

        it("does not publish any events") {
          verifyNoMoreInteractions(publisher)
        }
      }

      and("the pipeline is allowed to run multiple executions concurrently") {
        beforeGroup {
          pipeline.isLimitConcurrent = false
          runningPipeline.isLimitConcurrent = false

          whenever(
            repository
              .retrievePipelinesForPipelineConfigId(configId, ExecutionCriteria().setLimit(1).setStatuses(RUNNING))
          ) doReturn just(runningPipeline)
          whenever(
            repository.retrieve(message.executionType, message.executionId)
          ) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("starts the new pipeline") {
          verify(repository).updateStatus(message.executionId, RUNNING)
          verify(queue).push(isA<StartStage>())
        }
      }
    }
  }
})
