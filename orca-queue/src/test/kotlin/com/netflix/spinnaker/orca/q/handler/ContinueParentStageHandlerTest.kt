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

import com.netflix.spinnaker.orca.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.ext.beforeStages
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.ContinueParentStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.orca.q.buildAfterStages
import com.netflix.spinnaker.orca.q.buildBeforeStages
import com.netflix.spinnaker.orca.q.buildTasks
import com.netflix.spinnaker.orca.q.stageWithParallelAfter
import com.netflix.spinnaker.orca.q.stageWithSyntheticBefore
import com.netflix.spinnaker.orca.q.stageWithSyntheticBeforeAndNoTasks
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Duration

object ContinueParentStageHandlerTest : SubjectSpek<ContinueParentStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val retryDelay = Duration.ofSeconds(5)

  subject(CachingMode.GROUP) {
    ContinueParentStageHandler(queue, repository, retryDelay.toMillis())
  }

  fun resetMocks() = reset(queue, repository)

  listOf(SUCCEEDED, FAILED_CONTINUE).forEach { status ->
    describe("running a parent stage after its before stages complete with $status") {
      given("other before stages are not yet complete") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE)

        beforeGroup {
          pipeline.stageByRef("1<1").status = status
          pipeline.stageByRef("1<2").status = RUNNING
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("re-queues the message for later evaluation") {
          verify(queue).push(message, retryDelay)
        }
      }

      given("another before stage failed") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE)

        beforeGroup {
          pipeline.stageByRef("1<1").status = status
          pipeline.stageByRef("1<2").status = TERMINAL
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("does not re-queue the message") {
          verifyZeroInteractions(queue)
        }
      }

      given("the parent stage has tasks") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE)

        beforeGroup {
          pipeline.stageByRef("1").beforeStages().forEach { it.status = status }
        }

        and("they have not started yet") {
          beforeGroup {
            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("runs the parent stage's first task") {
            verify(queue).push(StartTask(pipeline.stageByRef("1"), "1"))
          }
        }

        and("they have already started") {
          beforeGroup {
            pipeline.stageByRef("1").tasks.first().status = RUNNING
            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("ignores the message") {
            verifyZeroInteractions(queue)
          }
        }
      }

      given("the parent stage has no tasks") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBeforeAndNoTasks.type
            stageWithSyntheticBeforeAndNoTasks.buildBeforeStages(this)
            stageWithSyntheticBeforeAndNoTasks.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE)

        beforeGroup {
          pipeline.stageByRef("1").beforeStages().forEach { it.status = status }
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("completes the stage with $status") {
          verify(queue).push(CompleteStage(pipeline.stageByRef("1")))
        }
      }
    }
  }

  listOf(SUCCEEDED, FAILED_CONTINUE).forEach { status ->
    describe("running a parent stage after its after stages complete with $status") {
      given("other after stages are not yet complete") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithParallelAfter.type
            stageWithParallelAfter.buildTasks(this)
            stageWithParallelAfter.buildAfterStages(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_AFTER)

        beforeGroup {
          pipeline.stageByRef("1>1").status = status
          pipeline.stageByRef("1>2").status = RUNNING
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("re-queues the message for later evaluation") {
          verify(queue).push(message, retryDelay)
        }
      }

      given("another after stage failed") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithParallelAfter.type
            stageWithParallelAfter.buildTasks(this)
            stageWithParallelAfter.buildAfterStages(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_AFTER)

        beforeGroup {
          pipeline.stageByRef("1>1").status = status
          pipeline.stageByRef("1>2").status = TERMINAL
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("tells the stage to complete") {
          verify(queue).push(CompleteStage(pipeline.stageByRef("1")))
        }
      }

      given("all after stages completed") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithParallelAfter.type
            stageWithParallelAfter.buildTasks(this)
            stageWithParallelAfter.buildAfterStages(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"), STAGE_AFTER)

        beforeGroup {
          pipeline.stageByRef("1>1").status = status
          pipeline.stageByRef("1>2").status = SUCCEEDED
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("tells the stage to complete") {
          verify(queue).push(CompleteStage(pipeline.stageByRef("1")))
        }
      }
    }
  }
})
