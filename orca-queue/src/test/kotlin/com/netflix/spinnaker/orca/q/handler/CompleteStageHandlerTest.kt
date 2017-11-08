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
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object CompleteStageHandlerTest : SubjectSpek<CompleteStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()
  val contextParameterProcessor: ContextParameterProcessor = mock()

  subject(GROUP) {
    CompleteStageHandler(queue, repository, publisher, clock, contextParameterProcessor)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("completing top level stages") {
    setOf(SUCCEEDED, FAILED_CONTINUE).forEach { taskStatus ->
      describe("when a stage's tasks complete with $taskStatus status") {
        and("it is already complete") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = multiTaskStage.type
              multiTaskStage.plan(this)
              tasks[0].status = SUCCEEDED
              tasks[1].status = taskStatus
              tasks[2].status = SUCCEEDED
              status = taskStatus
              endTime = clock.instant().minusSeconds(2).toEpochMilli()
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

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

        and("it is the last stage") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              it.status shouldEqual taskStatus
              it.endTime shouldEqual clock.millis()
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
              it.executionType shouldEqual pipeline.type
              it.executionId shouldEqual pipeline.id
              it.stageId shouldEqual message.stageId
              it.status shouldEqual taskStatus
            })
          }
        }

        and("there is a single downstream stage") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
            stage {
              refId = "2"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              it.status shouldEqual taskStatus
              it.endTime shouldEqual clock.millis()
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
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
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
          val message = CompleteStage(pipeline.stageByRef("1"))

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
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
            stage {
              refId = "2"
              type = singleTaskStage.type
              status = RUNNING
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

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

        setOf(CANCELED, TERMINAL, STOPPED).forEach { failureStatus ->
          and("there are parallel stages that failed") {
            val pipeline = pipeline {
              application = "covfefe"
              stage {
                refId = "1"
                type = singleTaskStage.type
                singleTaskStage.plan(this)
                tasks.first().status = SUCCEEDED
                status = RUNNING
              }
              stage {
                refId = "2"
                type = singleTaskStage.type
                status = failureStatus
              }
            }
            val message = CompleteStage(pipeline.stageByRef("1"))

            beforeGroup {
              whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
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

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      describe("when a stage's task fails with $taskStatus status") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = multiTaskStage.type
            multiTaskStage.plan(this)
            tasks[0].status = SUCCEEDED
            tasks[1].status = taskStatus
            tasks[2].status = NOT_STARTED
            status = RUNNING
          }
          stage {
            refId = "2"
            requisiteStageRefIds = listOf("1")
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"))

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.status shouldEqual taskStatus
            it.endTime shouldEqual clock.millis()
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
            it.executionType shouldEqual pipeline.type
            it.executionId shouldEqual pipeline.id
            it.stageId shouldEqual message.stageId
            it.status shouldEqual taskStatus
          })
        }
      }
    }

    mapOf(STAGE_BEFORE to stageWithSyntheticBefore, STAGE_AFTER to stageWithSyntheticAfter).forEach { syntheticType, stageBuilder ->
      setOf(TERMINAL, CANCELED, STOPPED).forEach { failureStatus ->
        describe("when a $syntheticType synthetic stage completed with $failureStatus") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              status = RUNNING
              type = stageBuilder.type
              stageBuilder.buildSyntheticStages(this)
              stageBuilder.plan(this)
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            pipeline
              .stages
              .first { it.syntheticStageOwner == syntheticType }
              .status = failureStatus
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              it.status shouldEqual failureStatus
              it.endTime shouldEqual clock.millis()
            })
          }
        }
      }

      describe("when any $syntheticType synthetic stage completed with FAILED_CONTINUE") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            status = RUNNING
            type = stageBuilder.type
            stageBuilder.buildSyntheticStages(this)
            stageBuilder.plan(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"))

        beforeGroup {
          pipeline
            .stages
            .first { it.syntheticStageOwner == syntheticType }
            .status = FAILED_CONTINUE
          pipeline.stageById(message.stageId).tasks.forEach { it.status = SUCCEEDED }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            it.status shouldEqual FAILED_CONTINUE
            it.endTime shouldEqual clock.millis()
          })
        }
      }
    }
  }

  describe("completing synthetic stages") {
    listOf(SUCCEEDED, FAILED_CONTINUE).forEach { taskStatus ->
      given("a synthetic stage's task completes with $taskStatus") {
        and("it comes before its parent stage") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = stageWithSyntheticBefore.type
              stageWithSyntheticBefore.buildSyntheticStages(this)
              stageWithSyntheticBefore.buildTasks(this)
            }
          }

          and("there are more before stages") {
            val message = CompleteStage(pipeline.stageByRef("1<1"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StartStage(
                pipeline.stageByRef("1<2")
              ))
            }
          }

          and("it is the last before stage") {
            val message = CompleteStage(pipeline.stageByRef("1<2"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("signals the parent stage to run") {
              verify(queue).push(ContinueParentStage(
                pipeline.stageByRef("1")
              ))
            }
          }
        }

        and("it comes after its parent stage") {
          val pipeline = pipeline {
            application = "foo"
            stage {
              refId = "1"
              type = stageWithSyntheticAfter.type
              stageWithSyntheticAfter.buildSyntheticStages(this)
              stageWithSyntheticAfter.buildTasks(this)
            }
          }

          and("there are more after stages") {
            val message = CompleteStage(pipeline.stageByRef("1>1"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
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

          and("it is the last after stage") {
            val message = CompleteStage(pipeline.stageByRef("1>2"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("signals the completion of the parent stage") {
              verify(queue).push(CompleteStage(
                message.executionType,
                message.executionId,
                "foo",
                pipeline.stages.first().id
              ))
            }
          }
        }
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      given("a synthetic stage's task ends with $taskStatus status") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildSyntheticStages(this)
            stageWithSyntheticBefore.plan(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).apply {
            status = RUNNING
            singleTaskStage.plan(this)
            tasks.first().status = taskStatus
          }

          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        on("receiving the message") {
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
    listOf(SUCCEEDED, FAILED_CONTINUE).forEach { status ->
      context("when one branch completes with $status") {
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
        val message = CompleteStage(pipeline.stageByRef("1=1"))

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
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
        val message = CompleteStage(pipeline.stageByRef("1=1"))

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          pipeline.stageByRef("1=2").status = SUCCEEDED
          pipeline.stageByRef("1=3").status = SUCCEEDED

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("signals the parent stage to try to run") {
          verify(queue).push(ContinueParentStage(pipeline.stageByRef("1")))
        }
      }
    }
  }

  describe("surfacing expression evaluation errors") {
    fun exceptionErrors(stages: List<Stage>): List<*> =
      stages.flatMap {
        ((it.context["exception"] as Map<*, *>)["details"] as Map<*, *>)["errors"] as List<*>
      }

    given("an exception in the stage context") {
      val expressionError = "Expression foo failed for field bar"
      val existingException = "Existing error"
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "wait"
          context = mapOf(
            "exception" to mapOf("details" to mapOf("errors" to mutableListOf(existingException))),
            PipelineExpressionEvaluator.SUMMARY to mapOf("failedExpression" to listOf(mapOf("description" to expressionError, "level" to "ERROR")))
          )
          status = RUNNING
          type = singleTaskStage.type
          singleTaskStage.plan(this)
          tasks.first().status = SUCCEEDED
        }
      }

      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("should contain evaluation summary as well as the existing error") {
        val errors = exceptionErrors(pipeline.stages)
        errors.size shouldEqual 2
        expressionError in errors
        existingException in errors
      }
    }

    given("no other exception errors in the stage context") {
      val expressionError = "Expression foo failed for field bar"
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "wait"
          context = mutableMapOf<String, Any>(
            PipelineExpressionEvaluator.SUMMARY to mapOf("failedExpression" to listOf(mapOf("description" to expressionError, "level" to "ERROR")))
          )
          status = RUNNING
          type = singleTaskStage.type
          singleTaskStage.plan(this)
          tasks.first().status = SUCCEEDED
        }
      }

      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("should only contain evaluation error message") {
        val errors = exceptionErrors(pipeline.stages)
        errors.size shouldEqual 1
        expressionError in errors
      }
    }
  }
})
