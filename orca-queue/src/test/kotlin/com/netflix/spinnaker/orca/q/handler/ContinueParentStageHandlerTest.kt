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
import com.netflix.spinnaker.orca.ext.beforeStages
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.*
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
    describe("running a parent stage after its pre-stages complete with $status") {
      given("other pre-stages are not yet complete") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"))

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

      given("another pre-stage failed") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"))

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
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"))

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
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBeforeAndNoTasks.type
            stageWithSyntheticBeforeAndNoTasks.buildBeforeStages(this)
            stageWithSyntheticBeforeAndNoTasks.buildTasks(this)
          }
        }

        val message = ContinueParentStage(pipeline.stageByRef("1"))

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
})
