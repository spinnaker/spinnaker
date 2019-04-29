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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.StageResolver
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.fixture.task
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.spek.but
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration.ZERO

object CompleteStageHandlerTest : SubjectSpek<CompleteStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val stageNavigator: StageNavigator = mock()
  val publisher: ApplicationEventPublisher = mock()
  val exceptionHandler: ExceptionHandler = DefaultExceptionHandler()
  val clock = fixedClock()
  val registry = NoopRegistry()
  val contextParameterProcessor: ContextParameterProcessor = mock()

  val emptyStage = object : StageDefinitionBuilder {}

  val stageWithTaskAndAfterStages = object : StageDefinitionBuilder {
    override fun getType() = "stageWithTaskAndAfterStages"

    override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
      graph.add {
        it.type = singleTaskStage.type
        it.name = "After Stage"
        it.context = mapOf("key" to "value")
      }
    }
  }

  val stageThatBlowsUpPlanningAfterStages = object : StageDefinitionBuilder {
    override fun getType() = "stageThatBlowsUpPlanningAfterStages"

    override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
      throw RuntimeException("there is some problem actually")
    }
  }

  val stageWithNothingButAfterStages = object : StageDefinitionBuilder {
    override fun getType() = "stageWithNothingButAfterStages"

    override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
      graph.add {
        it.type = singleTaskStage.type
        it.name = "After Stage"
      }
    }
  }

  subject(GROUP) {
    CompleteStageHandler(
      queue,
      repository,
      stageNavigator,
      publisher,
      clock,
      listOf(exceptionHandler),
      contextParameterProcessor,
      registry,
      DefaultStageDefinitionBuilderFactory(
        StageResolver(
          listOf(
            singleTaskStage,
            multiTaskStage,
            stageWithSyntheticBefore,
            stageWithSyntheticAfter,
            stageWithParallelBranches,
            stageWithTaskAndAfterStages,
            stageThatBlowsUpPlanningAfterStages,
            stageWithSyntheticOnFailure,
            stageWithNothingButAfterStages,
            stageWithSyntheticOnFailure,
            emptyStage
          )
        )
      )
    )
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("completing top level stages") {
    setOf(SUCCEEDED, FAILED_CONTINUE).forEach { taskStatus ->
      describe("when a stage's tasks complete with $taskStatus status") {
        and("it is already complete") {
          val pipeline = pipeline {
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
              assertThat(it.status).isEqualTo(taskStatus)
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
              assertThat(it.status).isEqualTo(taskStatus)
            })
          }
        }

        and("there is a single downstream stage") {
          val pipeline = pipeline {
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
              assertThat(it.status).isEqualTo(taskStatus)
              assertThat(it.endTime).isEqualTo(clock.millis())
            })
          }

          it("runs the next stage") {
            verify(queue).push(StartStage(
              message.executionType,
              message.executionId,
              message.application,
              pipeline.stages.last().id
            ))
          }

          it("does not run any tasks") {
            verify(queue, never()).push(any<RunTask>())
          }
        }

        and("there are multiple downstream stages") {
          val pipeline = pipeline {
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
              assertThat(allValues.map { it.stageId }.toSet()).isEqualTo(pipeline.stages[1..2].map { it.id }.toSet())
            }
          }
        }

        and("there are parallel stages still running") {
          val pipeline = pipeline {
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

        and("there are still synthetic stages to plan") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              name = "wait"
              status = RUNNING
              type = stageWithTaskAndAfterStages.type
              stageWithTaskAndAfterStages.plan(this)
              tasks.first().status = taskStatus
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

          it("adds a new AFTER_STAGE") {
            assertThat(pipeline.stages.map { it.type }).isEqualTo(listOf("stageWithTaskAndAfterStages", "singleTaskStage"))
          }

          it("starts the new AFTER_STAGE") {
            verify(queue).push(StartStage(message, pipeline.stages[1].id))
          }

          it("does not update the status of the stage itself") {
            verify(repository, never()).storeStage(pipeline.stageById(message.stageId))
          }

          it("does not signal completion of the execution") {
            verify(queue, never()).push(isA<CompleteExecution>())
          }
        }

        and("planning synthetic stages throws an exception") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              name = "wait"
              status = RUNNING
              type = stageThatBlowsUpPlanningAfterStages.type
              stageThatBlowsUpPlanningAfterStages.plan(this)
              tasks.first().status = taskStatus
            }
          }

          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            assertThat(pipeline.stages.map { it.type }).isEqualTo(listOf(stageThatBlowsUpPlanningAfterStages.type))
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("makes the stage TERMINAL") {
            assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
          }

          it("correctly records exception") {
            assertThat(pipeline.stageById(message.stageId).context).containsKey("exception")
            val exceptionContext = pipeline.stageById(message.stageId).context["exception"] as ExceptionHandler.Response
            assertThat(exceptionContext.exceptionType).isEqualTo(RuntimeException().javaClass.simpleName)
          }

          it("runs cancellation") {
            verify(queue).push(CancelStage(pipeline.stageById(message.stageId)))
          }

          it("signals completion of the execution") {
            verify(queue).push(CompleteExecution(pipeline))
          }
        }
      }
    }

    given("a stage had no synthetics or tasks") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "empty"
          status = RUNNING
          type = emptyStage.type
        }
        stage {
          refId = "2"
          type = singleTaskStage.type
          name = "downstream"
          requisiteStageRefIds = setOf("1")
        }
      }

      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("receiving the message") {
        subject.handle(message)
      }

      it("just marks the stage as SKIPPED") {
        verify(repository).storeStage(check {
          assertThat(it.id).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(SKIPPED)
        })
      }

      it("starts anything downstream") {
        verify(queue).push(StartStage(pipeline.stageByRef("2")))
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      describe("when a stage's task fails with $taskStatus status") {
        val pipeline = pipeline {
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
            assertThat(it.status).isEqualTo(taskStatus)
            assertThat(it.endTime).isEqualTo(clock.millis())
          })
        }

        it("does not run any downstream stages") {
          verify(queue, never()).push(isA<StartStage>())
        }

        it("fails the execution") {
          verify(queue).push(CompleteExecution(
            message.executionType,
            message.executionId,
            message.application
          ))
        }

        it("runs the stage's cancellation routine") {
          verify(queue).push(CancelStage(message))
        }

        it("publishes an event") {
          verify(publisher).publishEvent(check<StageComplete> {
            assertThat(it.executionType).isEqualTo(pipeline.type)
            assertThat(it.executionId).isEqualTo(pipeline.id)
            assertThat(it.stageId).isEqualTo(message.stageId)
            assertThat(it.status).isEqualTo(taskStatus)
          })
        }
      }
    }

    describe("when none of a stage's tasks ever started") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = multiTaskStage.type
          multiTaskStage.plan(this)
          tasks[0].status = NOT_STARTED
          tasks[1].status = NOT_STARTED
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
          assertThat(it.status).isEqualTo(TERMINAL)
          assertThat(it.endTime).isEqualTo(clock.millis())
        })
      }

      it("does not run any downstream stages") {
        verify(queue, never()).push(isA<StartStage>())
      }

      it("fails the execution") {
        verify(queue).push(CompleteExecution(
          message.executionType,
          message.executionId,
          message.application
        ))
      }

      it("runs the stage's cancellation routine") {
        verify(queue).push(CancelStage(message))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(TERMINAL)
        })
      }
    }

    given("a stage had no tasks or before stages") {
      but("does have after stages") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithNothingButAfterStages.type
            status = RUNNING
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

        it("plans and starts the after stages") {
          argumentCaptor<Message>().apply {
            verify(queue).push(capture())
            firstValue.let { capturedMessage ->
              when (capturedMessage) {
                is StartStage -> pipeline.stageById(capturedMessage.stageId).apply {
                  assertThat(parentStageId).isEqualTo(message.stageId)
                  assertThat(name).isEqualTo("After Stage")
                }
                else ->
                  fail("Expected a StartStage message but got a ${capturedMessage.javaClass.simpleName}")
              }
            }
          }
        }

        it("does not complete the pipeline") {
          verify(queue, never()).push(isA<CompleteExecution>())
        }

        it("does not mark the stage as failed") {
          assertThat(pipeline.stageById(message.stageId).status).isEqualTo(RUNNING)
        }
      }
    }

    mapOf(STAGE_BEFORE to stageWithSyntheticBefore, STAGE_AFTER to stageWithSyntheticAfter).forEach { syntheticType, stageBuilder ->
      setOf(TERMINAL, CANCELED, STOPPED).forEach { failureStatus ->
        describe("when a $syntheticType synthetic stage completed with $failureStatus") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              status = RUNNING
              type = stageBuilder.type
              if (syntheticType == STAGE_BEFORE) {
                stageBuilder.buildBeforeStages(this)
              } else {
                stageBuilder.buildAfterStages(this)
              }
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
              assertThat(it.status).isEqualTo(failureStatus)
              assertThat(it.endTime).isEqualTo(clock.millis())
            })
          }
        }
      }

      describe("when any $syntheticType synthetic stage completed with FAILED_CONTINUE") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            status = RUNNING
            type = stageBuilder.type
            if (syntheticType == STAGE_BEFORE) {
              stageBuilder.buildBeforeStages(this)
            } else {
              stageBuilder.buildAfterStages(this)
            }
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
            assertThat(it.status).isEqualTo(FAILED_CONTINUE)
            assertThat(it.endTime).isEqualTo(clock.millis())
          })
        }

        it("does not do anything silly like running the after stage again") {
          verify(queue, never()).push(isA<StartStage>())
        }
      }
    }

    describe("when all after stages have completed successfully") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          status = RUNNING
          type = stageWithSyntheticAfter.type
          stageWithSyntheticAfter.plan(this)
          stageWithSyntheticAfter.buildAfterStages(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        pipeline
          .stages
          .filter { it.syntheticStageOwner == STAGE_AFTER }
          .forEach { it.status = SUCCEEDED }
        pipeline.stageById(message.stageId).tasks.forEach { it.status = SUCCEEDED }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("does not do anything silly like running the after stage again") {
        verify(queue, never()).push(isA<StartStage>())
      }
    }

    given("after stages were planned but not run yet") {
      val pipeline = pipeline {
        stage {
          refId = "2"
          type = "deploy"
          name = "Deploy"
          status = RUNNING
          stage {
            refId = "2=1"
            type = "createServerGroup"
            name = "Deploy in us-west-2"
            status = RUNNING
            task {
              name = "determineSourceServerGroup"
              status = SUCCEEDED
            }
            stage {
              refId = "2=1>3"
              type = "applySourceServerGroupCapacity"
              name = "restoreMinCapacityFromSnapshot"
              syntheticStageOwner = STAGE_AFTER
              requisiteStageRefIds = setOf("2=1>2")
            }
            stage {
              refId = "2=1>2"
              type = "disableCluster"
              name = "disableCluster"
              syntheticStageOwner = STAGE_AFTER
              requisiteStageRefIds = setOf("2=1>1")
            }
            stage {
              refId = "2=1>1"
              type = "shrinkCluster"
              name = "shrinkCluster"
              syntheticStageOwner = STAGE_AFTER
            }
          }
        }
      }
      val message = CompleteStage(pipeline.stageByRef("2=1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("starts the first after stage") {
        verify(queue).push(StartStage(pipeline.stageByRef("2=1>1")))
      }
    }
  }

  describe("completing synthetic stages") {
    given("a synthetic stage's task completes with $SUCCEEDED") {
      and("it comes before its parent stage") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        and("there are more before stages") {
          val message = CompleteStage(pipeline.stageByRef("1<1"))

          beforeGroup {
            pipeline.stageById(message.stageId).apply {
              status = RUNNING
              singleTaskStage.plan(this)
              tasks.first().status = SUCCEEDED
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
              tasks.first().status = SUCCEEDED
            }

            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("signals the parent stage to run") {
            verify(queue).ensure(ContinueParentStage(
              pipeline.stageByRef("1"),
              STAGE_BEFORE
            ), ZERO)
          }
        }
      }

      and("it comes after its parent stage") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticAfter.type
            stageWithSyntheticAfter.buildBeforeStages(this)
            stageWithSyntheticAfter.buildTasks(this)
            stageWithSyntheticAfter.buildAfterStages(this)
          }
        }

        and("there are more after stages") {
          val message = CompleteStage(pipeline.stageByRef("1>1"))

          beforeGroup {
            pipeline.stageById(message.stageId).apply {
              status = RUNNING
              singleTaskStage.plan(this)
              tasks.first().status = SUCCEEDED
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
              message.application,
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
              tasks.first().status = SUCCEEDED
            }

            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
          }

          afterGroup(::resetMocks)


          on("receiving the message") {
            subject.handle(message)
          }

          it("tells the parent stage to continue") {
            verify(queue)
              .ensure(ContinueParentStage(
                pipeline.stageById(message.stageId).parent!!,
                STAGE_AFTER
              ), ZERO)
          }
        }
      }

      given("a synthetic stage's task ends with $FAILED_CONTINUE status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.plan(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).apply {
            status = RUNNING
            singleTaskStage.plan(this)
            tasks.first().status = FAILED_CONTINUE
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
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      given("a synthetic stage's task ends with $taskStatus status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
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

    given("a synthetic stage's task ends with $TERMINAL status and parent stage should continue on failure") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          context = mapOf("continuePipeline" to true) // should continue on failure
          type = stageWithSyntheticBefore.type
          stageWithSyntheticBefore.buildBeforeStages(this)
          stageWithSyntheticBefore.plan(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1<1"))

      beforeGroup {
        pipeline.stageById(message.stageId).apply {
          status = RUNNING
          singleTaskStage.plan(this)
          tasks.first().status = TERMINAL
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

      it("runs the parent stage's complete routine") {
        verify(queue).push(CompleteStage(message.copy(stageId = pipeline.stageByRef("1").id)))
      }
    }
  }

  describe("branching stages") {
    listOf(SUCCEEDED, FAILED_CONTINUE).forEach { status ->
      context("when one branch completes with $status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            name = "parallel"
            type = stageWithParallelBranches.type
            stageWithParallelBranches.buildBeforeStages(this)
            stageWithParallelBranches.buildTasks(this)
          }
        }

        val message = pipeline.stageByRef("1<1").let { completedSynthetic ->
          singleTaskStage.buildTasks(completedSynthetic)
          completedSynthetic.tasks.forEach { it.status = SUCCEEDED }
          CompleteStage(completedSynthetic)
        }

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("signals the parent stage to try to run") {
          verify(queue)
            .ensure(ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE), ZERO)
        }
      }

      given("all branches are complete") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            name = "parallel"
            type = stageWithParallelBranches.type
            stageWithParallelBranches.buildBeforeStages(this)
            stageWithParallelBranches.buildTasks(this)
          }
        }

        pipeline.stages.filter { it.parentStageId != null }.forEach {
          singleTaskStage.buildTasks(it)
          it.tasks.forEach { it.status = SUCCEEDED }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          pipeline.stageByRef("1<2").status = SUCCEEDED
          pipeline.stageByRef("1<3").status = SUCCEEDED

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("signals the parent stage to try to run") {
          verify(queue)
            .ensure(ContinueParentStage(pipeline.stageByRef("1"), STAGE_BEFORE), ZERO)
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
        assertThat(errors.size).isEqualTo(2)
        expressionError in errors
        existingException in errors
      }
    }

    given("no other exception errors in the stage context") {
      val expressionError = "Expression foo failed for field bar"
      val pipeline = pipeline {
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
        assertThat(errors.size).isEqualTo(1)
        expressionError in errors
      }
    }

    given("a stage configured to be TERMINAL if it contains any expression errors") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          name = "wait"
          context = mapOf(
            "failOnFailedExpressions" to true,
            PipelineExpressionEvaluator.SUMMARY to mapOf(
              "failedExpression" to listOf(mapOf("description" to "failed expression foo", "level" to "ERROR"))
            )
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

      it("should fail the stage") {
        assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
      }
    }
  }

  given("a stage ends with TERMINAL status") {
    and("it has not run its on failure stages yet") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = stageWithSyntheticOnFailure.type
          stageWithSyntheticOnFailure.buildBeforeStages(this)
          stageWithSyntheticOnFailure.plan(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        pipeline.stageById(message.stageId).apply {
          status = RUNNING
          tasks.first().status = TERMINAL
        }

        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("plans the first 'OnFailure' stage") {
        val onFailureStage = pipeline.stages.first { it.name == "onFailure1" }
        verify(queue).push(StartStage(onFailureStage))
      }

      it("does not (yet) update the stage status") {
        assertThat(pipeline.stageById(message.stageId).status).isEqualTo(RUNNING)
      }
    }

    and("it has already run its on failure stages") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = stageWithSyntheticOnFailure.type
          stageWithSyntheticOnFailure.buildBeforeStages(this)
          stageWithSyntheticOnFailure.plan(this)
          stageWithSyntheticOnFailure.buildFailureStages(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        pipeline.stageById(message.stageId).apply {
          status = RUNNING
          tasks.first().status = TERMINAL
        }

        pipeline.stages.filter { it.parentStageId == message.stageId }.forEach {
          it.status = SUCCEEDED
        }

        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message again") {
        subject.handle(message)
      }

      it("does not re-plan any 'OnFailure' stages") {
        val onFailureStage = pipeline.stages.first { it.name == "onFailure1" }
        verify(queue, never()).push(StartStage(onFailureStage))
        verify(queue).push(CancelStage(message))
      }

      it("updates the stage status") {
        assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
      }
    }
  }

  describe("stage planning failure behavior") {
    given("a stage that failed to plan its before stages") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          context = mutableMapOf<String, Any>("beforeStagePlanningFailed" to true)
          type = stageWithSyntheticOnFailure.type
          stageWithSyntheticOnFailure.buildFailureStages(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      and("it has not run its on failure stages yet") {
        beforeGroup {
          pipeline.stageById(message.stageId).apply {
            status = RUNNING
          }

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("plans the first on failure stage") {
          val onFailureStage = pipeline.stages.first { it.name == "onFailure1" }
          verify(queue).push(StartStage(onFailureStage))
        }

        it("does not (yet) update the stage status") {
          assertThat(pipeline.stageById(message.stageId).status).isEqualTo(RUNNING)
        }
      }

      and("it has already run its on failure stages") {
        beforeGroup {
          pipeline.stageById(message.stageId).apply {
            status = RUNNING
          }
          pipeline.stages.filter { it.parentStageId == message.stageId }.forEach {
            it.status = SUCCEEDED
          }

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message again") {
          subject.handle(message)
        }

        it("does not re-plan on failure stages") {
          val onFailureStage = pipeline.stages.first { it.name == "onFailure1" }
          verify(queue, never()).push(StartStage(onFailureStage))
          verify(queue).push(CancelStage(message))
        }

        it("updates the stage status") {
          assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
        }
      }
    }
  }
})
