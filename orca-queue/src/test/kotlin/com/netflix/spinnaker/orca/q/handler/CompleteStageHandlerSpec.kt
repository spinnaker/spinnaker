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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.springframework.context.ApplicationEventPublisher

class CompleteStageHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  val handler = CompleteStageHandler(queue, repository, publisher, clock)

  fun resetMocks() = reset(queue, repository, publisher)

  setOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED).forEach { status ->
    describe("when a stage completes with $status status") {
      context("it is the last stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, status)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          handler.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.getStatus() shouldEqual status
            it.getEndTime() shouldEqual clock.millis()
          })
        }

        it("completes the execution") {
          verify(queue).push(CompleteExecution(
            message.executionType,
            message.executionId,
            "foo",
            SUCCEEDED // execution is SUCCEEDED even if stage was FAILED_CONTINUE or SKIPPED
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
            it.status shouldEqual status
          })
        }
      }

      context("there is a single downstream stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
          }
          stage {
            refId = "2"
            requisiteStageRefIds = setOf("1")
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, status)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          handler.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.getStatus() shouldEqual status
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

      context("there are multiple downstream stages") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = singleTaskStage.type
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
        val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, status)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          handler.handle(message)
        }

        it("runs the next stages") {
          argumentCaptor<StartStage>().apply {
            verify(queue, times(2)).push(capture())
            allValues.map { it.stageId }.toSet() shouldEqual pipeline.stages[1..2].map { it.id }.toSet()
          }
        }
      }
    }
  }

  setOf(TERMINAL, CANCELED).forEach { status ->
    describe("when a stage fails with $status status") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, status)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the stage state") {
        verify(repository).storeStage(check {
          it.getStatus() shouldEqual status
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
          "foo",
          status
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
          it.status shouldEqual status
        })
      }
    }
  }

  describe("synthetic stages") {
    context("when a synthetic stage completes successfully") {
      context("before a main stage") {
        context("that has tasks") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              type = stageWithSyntheticBefore.type
              stageWithSyntheticBefore.buildSyntheticStages(this)
              stageWithSyntheticBefore.buildTasks(this)
            }
          }

          context("there are more before stages") {
            val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            action("the handler receives a message") {
              handler.handle(message)
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StartStage(
                message.executionType,
                message.executionId,
                "foo",
                pipeline.stages[1].id
              ))
            }
          }

          context("it is the last before stage") {
            val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[1].id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            action("the handler receives a message") {
              handler.handle(message)
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StartTask(
                message.executionType,
                message.executionId,
                "foo",
                pipeline.stages[2].id,
                "1"
              ))
            }
          }
        }

        context("that has no tasks") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = stageWithSyntheticBeforeAndNoTasks.type
              stageWithSyntheticBeforeAndNoTasks.buildSyntheticStages(this)
              stageWithSyntheticBeforeAndNoTasks.buildTasks(this)
            }
          }

          context("it is the last before stage") {
            val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1<1").id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            action("the handler receives a message") {
              handler.handle(message)
            }

            it("completes the stage") {
              verify(queue).push(CompleteStage(
                message.executionType,
                message.executionId,
                "foo",
                pipeline.stageByRef("1").id,
                SUCCEEDED
              ))
            }
          }
        }

        context("that has no tasks but does have after stages") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = stageWithSyntheticBeforeAndAfterAndNoTasks.type
              stageWithSyntheticBeforeAndAfterAndNoTasks.buildSyntheticStages(this)
              stageWithSyntheticBeforeAndAfterAndNoTasks.buildTasks(this)
            }
          }

          context("it is the last before stage") {
            val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1<1").id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            action("the handler receives a message") {
              handler.handle(message)
            }

            it("starts the first after stage") {
              verify(queue).push(StartStage(
                message.executionType,
                message.executionId,
                "foo",
                pipeline.stageByRef("1>1").id
              ))
            }
          }
        }
      }

      context("after the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticAfter.type
            stageWithSyntheticAfter.buildSyntheticStages(this)
            stageWithSyntheticAfter.buildTasks(this)
          }
        }

        context("there are more after stages") {
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[1].id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
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
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.last().id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
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
        val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1<1").id, status)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        action("the handler receives a message") {
          handler.handle(message)
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
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[0].id, SUCCEEDED)

      beforeGroup {
        whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("waits for other branches to finish") {
        verify(queue, never()).push(any())
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
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[0].id, SUCCEEDED)

      beforeGroup {
        pipeline.stages.forEach {
          if (it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE && it.id != message.stageId) {
            it.status = SUCCEEDED
          }
        }

        whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("runs any post-branch tasks") {
        verify(queue).push(isA<StartTask>())
      }
    }
  }
})
