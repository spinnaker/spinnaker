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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.AbortStage
import com.netflix.spinnaker.orca.q.CancelStage
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.time.toInstant
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object AbortStageHandlerTest : SubjectSpek<AbortStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  subject(GROUP) {
    AbortStageHandler(queue, repository, publisher, clock)
  }

  fun resetMocks() {
    reset(queue, repository, publisher)
  }

  describe("aborting a stage") {
    given("a stage that already completed") {
      val pipeline = pipeline {
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = SUCCEEDED
        }
      }

      val message = AbortStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("does nothing at all") {
        verifyZeroInteractions(queue)
        verifyZeroInteractions(publisher)
        verify(repository, never()).storeStage(any())
      }
    }

    given("a top level stage") {
      val pipeline = pipeline {
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }

      val message = AbortStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("marks the stage as TERMINAL") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(TERMINAL)
          assertThat(it.endTime.toInstant()).isEqualTo(clock.instant())
        })
      }

      it("cancels the stage") {
        verify(queue).push(CancelStage(message))
      }

      it("completes the execution") {
        verify(queue).push(CompleteExecution(message))
      }

      it("emits an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          assertThat(it.status).isEqualTo(TERMINAL)
        })
      }
    }

    given("a synthetic level stage") {
      val pipeline = pipeline {
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
          stage {
            refId = "1<1"
            type = "whatever"
            status = RUNNING
            syntheticStageOwner = STAGE_BEFORE
          }
        }
      }

      val message = AbortStage(pipeline.stageByRef("1<1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("marks the stage as TERMINAL") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(TERMINAL)
          assertThat(it.endTime.toInstant()).isEqualTo(clock.instant())
        })
      }

      it("cancels the stage") {
        verify(queue).push(CancelStage(message))
      }

      it("completes the parent stage") {
        verify(queue).push(CompleteStage(pipeline.stageByRef("1")))
      }

      it("emits an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          assertThat(it.status).isEqualTo(TERMINAL)
        })
      }
    }
  }
})
