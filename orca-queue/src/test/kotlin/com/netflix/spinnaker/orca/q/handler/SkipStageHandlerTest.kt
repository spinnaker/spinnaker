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
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
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

object SkipStageHandlerTest : SubjectSpek<SkipStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  subject(GROUP) {
    SkipStageHandler(queue, repository, publisher, clock)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("skipping a stage") {
    given("it is already complete") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = "whatever"
          status = SUCCEEDED
          endTime = clock.instant().minusSeconds(2).toEpochMilli()
        }
      }
      val message = SkipStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("ignores the message") {
        verify(repository, never()).storeStage(any())
        verifyZeroInteractions(queue)
        verifyZeroInteractions(publisher)
      }
    }

    given("it is the last stage") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }
      val message = SkipStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the stage state") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(SKIPPED)
          assertThat(it.endTime).isEqualTo(clock.millis())
        })
      }

      it("completes the execution") {
        verify(queue).push(CompleteExecution(pipeline))
      }

      it("does not emit any commands") {
        verify(queue, never()).push(any<RunTask>())
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(SKIPPED)
        })
      }
    }

    given("there is a single downstream stage") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          type = "whatever"
        }
      }
      val message = SkipStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the stage state") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(SKIPPED)
          assertThat(it.endTime).isEqualTo(clock.millis())
        })
      }

      it("runs the next stage") {
        verify(queue).push(StartStage(
          message.executionType,
          message.executionId,
          "foo",
          pipeline.stages.last().id
        ))
      }

      it("does not run any tasks") {
        verify(queue, never()).push(any<RunTask>())
      }
    }

    given("there are multiple downstream stages") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          type = "whatever"
        }
        stage {
          refId = "3"
          requisiteStageRefIds = setOf("1")
          type = "whatever"
        }
      }
      val message = SkipStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("runs the next stages") {
        argumentCaptor<StartStage>().apply {
          verify(queue, times(2)).push(capture())
          assertThat(allValues.map { it.stageId }.toSet()).isEqualTo(pipeline.stages[1..2].map { it.id }.toSet())
        }
      }
    }

    given("there are parallel stages still running") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
        stage {
          refId = "2"
          type = "whatever"
          status = RUNNING
        }
      }
      val message = SkipStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("still signals completion of the execution") {
        verify(queue).push(CompleteExecution(pipeline))
      }
    }
  }
})
