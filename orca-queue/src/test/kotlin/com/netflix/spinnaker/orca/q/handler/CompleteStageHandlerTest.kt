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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object CompleteStageHandlerTest : SubjectSpek<CompleteStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  subject(GROUP) {
    CompleteStageHandler(queue, repository, publisher, clock)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  setOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED).forEach { stageStatus ->
    describe("when a stage completes with $stageStatus status") {
      and("it is already complete") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
            status = stageStatus
            endTime = clock.instant().minusSeconds(2).toEpochMilli()
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

      and("it is the last stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
            status = RUNNING
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.getStatus() shouldEqual stageStatus
            it.getEndTime() shouldEqual clock.millis()
          })
        }

        it("completes the execution") {
          verify(queue).push(CompleteExecution(
            pipeline // execution is SUCCEEDED even if stage was FAILED_CONTINUE or SKIPPED
          ))
        }

        it("does not emit any commands") {
          verify(queue, never()).push(any<RunTask>())
        }

        it("publishes an event") {
          verify(publisher).publishEvent(check<StageComplete> {
            it.executionType shouldEqual pipeline.javaClass
            it.executionId shouldEqual pipeline.id
            it.stageId shouldEqual message.stageId
            it.status shouldEqual stageStatus
          })
        }
      }

      and("there is a single downstream stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
            status = RUNNING
          }
          stage {
            refId = "2"
            requisiteStageRefIds = setOf("1")
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.getStatus() shouldEqual stageStatus
            it.getEndTime() shouldEqual clock.millis()
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

      and("there are multiple downstream stages") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
            status = RUNNING
          }
          stage {
            refId = "2"
            requisiteStageRefIds = setOf("1")
            type = singleTaskStage.type
          }
          stage {
            refId = "3"
            requisiteStageRefIds = setOf("1")
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("runs the next stages") {
          argumentCaptor<StartStage>().apply {
            verify(queue, times(2)).push(capture())
            allValues.map { it.stageId }.toSet() shouldEqual pipeline.stages[1..2].map { it.id }.toSet()
          }
        }
      }

      and("there are parallel stages still running") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
            status = RUNNING
          }
          stage {
            refId = "2"
            type = singleTaskStage.type
            status = RUNNING
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("still signals completion of the execution") {
          verify(queue).push(CompleteExecution(pipeline))
        }
      }

      setOf(CANCELED, TERMINAL, STOPPED).forEach { failureStatus ->
        and("there are parallel stages that failed") {
          val pipeline = pipeline {
            application = "covfefe"
            stage {
              refId = "1"
              type = singleTaskStage.type
              status = RUNNING
            }
            stage {
              refId = "2"
              type = singleTaskStage.type
              status = failureStatus
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

          beforeGroup {
            whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("still signals completion of the execution") {
            verify(queue).push(CompleteExecution(pipeline))
          }
        }
      }
    }
  }

  setOf(TERMINAL, CANCELED).forEach { stageStatus ->
    describe("when a stage fails with $stageStatus status") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
          status = RUNNING
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"), stageStatus)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the stage state") {
        verify(repository).storeStage(check {
          it.getStatus() shouldEqual stageStatus
          it.getEndTime() shouldEqual clock.millis()
        })
      }

      it("does not run any downstream stages") {
        verify(queue, never()).push(isA<StartStage>())
      }

      it("fails the execution") {
        verify(queue).push(CompleteExecution(
          message.executionType,
          message.executionId,
          "foo"
        ))
      }

      it("runs the stage's cancellation routine") {
        verify(queue).push(CancelStage(message))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          it.executionType shouldEqual pipeline.javaClass
          it.executionId shouldEqual pipeline.id
          it.stageId shouldEqual message.stageId
          it.status shouldEqual stageStatus
        })
      }
    }
  }

  describe("synthetic stages") {
    context("when a synthetic stage completes successfully") {
      context("before a main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticAfter.type
            stageWithSyntheticBefore.buildSyntheticStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        context("there are more after stages") {
          val message = CompleteStage(pipeline.stageByRef("1<1"), SUCCEEDED)

          beforeGroup {
            pipeline.stageById(message.stageId).status = RUNNING
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("runs the next synthetic stage") {
            verify(queue).push(StartStage(
              pipeline.stageByRef("1<2")
            ))
          }
        }

        context("it is the last after stage") {
          val message = CompleteStage(pipeline.stageByRef("1<2"), SUCCEEDED)

          beforeGroup {
            pipeline.stageById(message.stageId).status = RUNNING
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("signals the parent stage to run") {
            verify(queue).push(ContinueParentStage(
              pipeline.stageByRef("1")
            ))
          }
        }
      }

      context("after the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticAfter.type
            stageWithSyntheticAfter.buildSyntheticStages(this)
            stageWithSyntheticAfter.buildTasks(this)
          }
        }

        context("there are more after stages") {
          val message = CompleteStage(pipeline.stageByRef("1>1"), SUCCEEDED)

          beforeGroup {
            pipeline.stageById(message.stageId).status = RUNNING
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("runs the next synthetic stage") {
            verify(queue).push(StartStage(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages.last().id
            ))
          }
        }

        context("it is the last after stage") {
          val message = CompleteStage(pipeline.stageByRef("1>2"), SUCCEEDED)

          beforeGroup {
            pipeline.stageById(message.stageId).status = RUNNING
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("signals the completion of the parent stage") {
            verify(queue).push(CompleteStage(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages.first().id,
              SUCCEEDED
            ))
          }
        }
      }
    }

    setOf(TERMINAL, CANCELED).forEach { status ->
      context("when a synthetic stage ends with $status status") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildSyntheticStages(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"), status)

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        action("the handler receives a message") {
          subject.handle(message)
        }

        afterGroup(::resetMocks)

        it("rolls up to the parent stage") {
          verify(queue).push(message.copy(stageId = pipeline.stageByRef("1").id))
        }

        it("runs the stage's cancel routine") {
          verify(queue).push(CancelStage(message))
        }
      }
    }
  }

  describe("branching stages") {
    context("when one branch completes") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "parallel"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildSyntheticStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1=1"), SUCCEEDED)

      beforeGroup {
        pipeline.stageById(message.stageId).status = RUNNING
        whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("signals the parent stage to try to run") {
        verify(queue).push(ContinueParentStage(pipeline.stageByRef("1")))
      }
    }

    context("when all branches are complete") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "parallel"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildSyntheticStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1=1"), SUCCEEDED)

      beforeGroup {
        pipeline.stageById(message.stageId).status = RUNNING
        pipeline.stageByRef("1=2").status = SUCCEEDED
        pipeline.stageByRef("1=3").status = SUCCEEDED

        whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("signals the parent stage to try to run") {
        verify(queue).push(ContinueParentStage(pipeline.stageByRef("1")))
      }
    }
  }
})
