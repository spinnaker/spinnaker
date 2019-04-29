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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.assertj.assertSoftly
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.StageResolver
import com.netflix.spinnaker.orca.events.StageStarted
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher
import java.lang.RuntimeException
import java.time.Duration

object StartStageHandlerTest : SubjectSpek<StartStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val stageNavigator: StageNavigator = mock()
  val publisher: ApplicationEventPublisher = mock()
  val exceptionHandler: ExceptionHandler = mock()
  val objectMapper = ObjectMapper()
  val clock = fixedClock()
  val registry = NoopRegistry()
  val retryDelay = Duration.ofSeconds(5)

  subject(GROUP) {
    StartStageHandler(
      queue,
      repository,
      stageNavigator,
      DefaultStageDefinitionBuilderFactory(
        StageResolver(
          listOf(
            singleTaskStage,
            multiTaskStage,
            stageWithSyntheticBefore,
            stageWithSyntheticAfter,
            stageWithParallelBranches,
            rollingPushStage,
            zeroTaskStage,
            stageWithSyntheticAfterAndNoTasks,
            webhookStage,
            failPlanningStage
          )
        )
      ),
      ContextParameterProcessor(),
      publisher,
      listOf(exceptionHandler),
      objectMapper,
      clock,
      registry,
      retryDelayMs = retryDelay.toMillis()
    )
  }

  fun resetMocks() = reset(queue, repository, publisher, exceptionHandler)

  describe("starting a stage") {
    given("there is a single initial task") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = singleTaskStage.type
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("updates the stage status") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(RUNNING)
          assertThat(it.startTime).isEqualTo(clock.millis())
        })
      }

      it("attaches tasks to the stage") {
        verify(repository).storeStage(check {
          assertThat(it.tasks.size).isEqualTo(1)
          it.tasks.first().apply {
            assertThat(id).isEqualTo("1")
            assertThat(name).isEqualTo("dummy")
            assertThat(implementingClass).isEqualTo(DummyTask::class.java.name)
            assertThat(isStageStart).isEqualTo(true)
            assertThat(isStageEnd).isEqualTo(true)
          }
        })
      }

      it("starts the first task") {
        verify(queue).push(StartTask(message, "1"))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<StageStarted> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
        })
      }
    }

    given("the stage has no tasks") {
      and("no after stages") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = zeroTaskStage.type
          }
        }
        val message = StartStage(pipeline.stageByRef("1"))

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("updates the stage status") {
          verify(repository).storeStage(check {
            assertThat(it.status).isEqualTo(RUNNING)
            assertThat(it.startTime).isEqualTo(clock.millis())
          })
        }

        it("immediately completes the stage") {
          verify(queue).push(CompleteStage(message))
          verifyNoMoreInteractions(queue)
        }

        it("publishes an event") {
          verify(publisher).publishEvent(check<StageStarted> {
            assertThat(it.executionType).isEqualTo(pipeline.type)
            assertThat(it.executionId).isEqualTo(pipeline.id)
            assertThat(it.stageId).isEqualTo(message.stageId)
          })
        }
      }

      and("at least one after stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticAfterAndNoTasks.type
          }
        }
        val message = StartStage(pipeline.stageByRef("1"))

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("updates the stage status") {
          verify(repository).storeStage(check {
            assertThat(it.status).isEqualTo(RUNNING)
            assertThat(it.startTime).isEqualTo(clock.millis())
          })
        }

        it("completes the stage") {
          verify(queue).push(CompleteStage(message))
          verifyNoMoreInteractions(queue)
        }

        it("publishes an event") {
          verify(publisher).publishEvent(check<StageStarted> {
            assertThat(it.executionType).isEqualTo(pipeline.type)
            assertThat(it.executionId).isEqualTo(pipeline.id)
            assertThat(it.stageId).isEqualTo(message.stageId)
          })
        }
      }
    }

    given("the stage has several linear tasks") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = multiTaskStage.type
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      on("receiving a message") {
        subject.handle(message)
      }

      afterGroup(::resetMocks)

      it("attaches tasks to the stage") {
        verify(repository).storeStage(check {
          assertThat(it.tasks.size).isEqualTo(3)
          it.tasks[0].apply {
            assertThat(id).isEqualTo("1")
            assertThat(name).isEqualTo("dummy1")
            assertThat(implementingClass).isEqualTo(DummyTask::class.java.name)
            assertThat(isStageStart).isEqualTo(true)
            assertThat(isStageEnd).isEqualTo(false)
          }
          it.tasks[1].apply {
            assertThat(id).isEqualTo("2")
            assertThat(name).isEqualTo("dummy2")
            assertThat(implementingClass).isEqualTo(DummyTask::class.java.name)
            assertThat(isStageStart).isEqualTo(false)
            assertThat(isStageEnd).isEqualTo(false)
          }
          it.tasks[2].apply {
            assertThat(id).isEqualTo("3")
            assertThat(name).isEqualTo("dummy3")
            assertThat(implementingClass).isEqualTo(DummyTask::class.java.name)
            assertThat(isStageStart).isEqualTo(false)
            assertThat(isStageEnd).isEqualTo(true)
          }
        })
      }

      it("starts the first task") {
        verify(queue).push(StartTask(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          "1"
        ))
      }
    }

    given("the stage has synthetic stages") {
      context("before the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticBefore.type
          }
        }
        val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        on("receiving a message") {
          subject.handle(message)
        }

        afterGroup(::resetMocks)

        it("attaches the synthetic stage to the pipeline") {
          verify(repository, times(2)).addStage(check {
            assertThat(it.parentStageId).isEqualTo(message.stageId)
            assertThat(it.syntheticStageOwner).isEqualTo(STAGE_BEFORE)
          })
        }

        it("raises an event to indicate the synthetic stage is starting") {
          verify(queue).push(StartStage(
            message.executionType,
            message.executionId,
            "foo",
            pipeline.stages.first().id
          ))
        }
      }

      context("after the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticAfter.type
          }
        }
        val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("defers planning the after stages") {
          verify(repository, never()).addStage(any())
        }

        it("raises an event to indicate the first task is starting") {
          verify(queue).push(StartTask(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId,
            "1"
          ))
        }
      }
    }

    given("the stage has other upstream stages") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          type = singleTaskStage.type
        }
        stage {
          refId = "3"
          requisiteStageRefIds = setOf("1", "2")
          type = singleTaskStage.type
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("3").id)

      and("at least one is incomplete") {
        beforeGroup {
          pipeline.stageByRef("1").status = SUCCEEDED
          pipeline.stageByRef("2").status = RUNNING
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("doesn't build its tasks") {
          assertThat(pipeline.stageByRef("3").tasks).isEmpty()
        }

        it("waits for the other upstream stage to complete") {
          verify(queue, never()).push(isA<StartTask>())
        }

        it("does not publish an event") {
          verifyZeroInteractions(publisher)
        }

        it("re-queues the message with a delay") {
          verify(queue).push(message, retryDelay)
        }
      }

      and("they are all complete") {
        beforeGroup {
          pipeline.stageByRef("1").status = SUCCEEDED
          pipeline.stageByRef("2").status = SUCCEEDED
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("starts its first task") {
          verify(queue).push(isA<StartTask>())
        }

        it("publishes an event") {
          verify(publisher).publishEvent(isA<StageStarted>())
        }
      }

      and("one of them failed") {
        beforeGroup {
          pipeline.stageByRef("1").status = SUCCEEDED
          pipeline.stageByRef("2").status = TERMINAL
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("publishes no events") {
          verifyZeroInteractions(publisher)
        }

        it("completes the execution") {
          verify(queue).push(CompleteExecution(message))
          verifyNoMoreInteractions(queue)
        }
      }

      and("completion of another has already started this stage") {
        beforeGroup {
          pipeline.stageByRef("1").status = SUCCEEDED
          pipeline.stageByRef("2").status = SUCCEEDED
          pipeline.stageByRef("3").status = RUNNING
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not queue any messages") {
          verifyZeroInteractions(queue)
        }

        it("does not publish any events") {
          verifyZeroInteractions(publisher)
        }
      }
    }

    given("the stage has an execution window") {
      and("synthetic before stages") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            context["restrictExecutionDuringTimeWindow"] = true
          }
        }
        val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("injects a 'wait for execution window' stage before any other synthetic stages") {
          argumentCaptor<Stage>().apply {
            verify(repository, times(3)).addStage(capture())
            assertSoftly {
              assertThat(firstValue.type).isEqualTo(RestrictExecutionDuringTimeWindow.TYPE)
              assertThat(firstValue.parentStageId).isEqualTo(message.stageId)
              assertThat(firstValue.syntheticStageOwner).isEqualTo(STAGE_BEFORE)
              assertThat(secondValue.requisiteStageRefIds).isEqualTo(setOf(firstValue.refId))
            }
          }
        }

        it("starts the 'wait for execution window' stage") {
          verify(queue).push(check<StartStage> {
            assertThat(it.stageId).isEqualTo(pipeline.stages.find { it.type == RestrictExecutionDuringTimeWindow.TYPE }!!.id)
          })
          verifyNoMoreInteractions(queue)
        }
      }

      and("parallel stages") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            type = stageWithParallelBranches.type
            context["restrictExecutionDuringTimeWindow"] = true
          }
        }
        val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving a message") {
          subject.handle(message)
        }

        it("injects a 'wait for execution window' stage before any other synthetic stages") {
          argumentCaptor<Stage>().apply {
            verify(repository, times(4)).addStage(capture())
            assertSoftly {
              assertThat(firstValue.type)
                .isEqualTo(RestrictExecutionDuringTimeWindow.TYPE)
              assertThat(firstValue.parentStageId).isEqualTo(message.stageId)
              assertThat(firstValue.syntheticStageOwner).isEqualTo(STAGE_BEFORE)
              allValues[1..3].forEach {
                assertThat(it.requisiteStageRefIds)
                  .isEqualTo(setOf(firstValue.refId))
              }
              assertThat(allValues[1..3].map { it.type })
                .allMatch { it == singleTaskStage.type }
            }
          }
        }

        it("starts the 'wait for execution window' stage") {
          verify(queue).push(check<StartStage> {
            assertThat(it.stageId).isEqualTo(pipeline.stages.find { it.type == RestrictExecutionDuringTimeWindow.TYPE }!!.id)
          })
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("the stage has a stage type alias") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = "bar"
          context["alias"] = webhookStage.type
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("starts the stage") {
        verify(repository).storeStage(check {
          assertThat(it.type).isEqualTo("bar")
          assertThat(it.status).isEqualTo(RUNNING)
          assertThat(it.startTime).isEqualTo(clock.millis())
        })
      }

      it("attaches a task to the stage") {
        verify(repository).storeStage(check {
          assertThat(it.tasks.size).isEqualTo(1)
          it.tasks.first().apply {
            assertThat(id).isEqualTo("1")
            assertThat(name).isEqualTo("createWebhook")
            assertThat(implementingClass).isEqualTo(DummyTask::class.java.name)
            assertThat(isStageStart).isEqualTo(true)
            assertThat(isStageEnd).isEqualTo(true)
          }
        })
      }
    }

    given("the stage has a start time after ttl") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "bar"
          type = singleTaskStage.type
          startTimeExpiry = clock.instant().minusSeconds(30).toEpochMilli()
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("cancels the stage") {
        verify(queue).push(SkipStage(
          pipeline.stageByRef("bar")
        ))
      }
    }

    given("an exception is thrown planning the stage") {
      val pipeline = pipeline {
        application = "covfefe"
        stage {
          refId = "1"
          type = failPlanningStage.type
        }
      }
      val message = StartStage(pipeline.stageByRef("1"))

      and("it is not recoverable") {
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.responseDetails("o noes"),
          false
        )

        and("the pipeline should fail") {
          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("completes the stage") {
            verify(queue).push(isA<CompleteStage>())
          }

          it("attaches the exception to the stage context") {
            verify(repository).storeStage(check {
              assertThat(it.context["exception"]).isEqualTo(exceptionDetails)
            })
          }
        }

        and("only the branch should fail") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
              context["beforeStagePlanningFailed"] = null
            }

            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("completes the stage") {
            verify(queue).push(isA<CompleteStage>())
          }

          it("attaches the exception to the stage context") {
            verify(repository).storeStage(check {
              assertThat(it.context["exception"]).isEqualTo(exceptionDetails)
            })
          }

          it("attaches flag to the stage context to indicate that before stage planning failed") {
            verify(repository).storeStage(check {
              assertThat(it.context["beforeStagePlanningFailed"]).isEqualTo(true)
            })
          }
        }

        and("the branch should be allowed to continue") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = true
              context["beforeStagePlanningFailed"] = null
            }

            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("completes the stage") {
            verify(queue).push(isA<CompleteStage>())
          }

          it("attaches the exception to the stage context") {
            verify(repository).storeStage(check {
              assertThat(it.context["exception"]).isEqualTo(exceptionDetails)
            })
          }

          it("attaches flag to the stage context to indicate that before stage planning failed") {
            verify(repository).storeStage(check {
              assertThat(it.context["beforeStagePlanningFailed"]).isEqualTo(true)
            })
          }
        }
      }

      and("it is recoverable") {
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.responseDetails("o noes"),
          true
        )

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(exceptionHandler.handles(any())) doReturn true
          whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("re-runs the task") {
          verify(queue).push(message, retryDelay)
        }
      }
    }
  }

  describe("running a branching stage") {
    context("when the stage starts") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "parallel"
          type = stageWithParallelBranches.type
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("builds tasks for the main branch") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(stage.tasks.map(Task::getName)).isEqualTo(listOf("post-branch"))
      }

      it("builds synthetic stages for each parallel branch") {
        assertThat(pipeline.stages.size).isEqualTo(4)
        assertThat(pipeline.stages.map { it.type })
          .isEqualTo(listOf(singleTaskStage.type, singleTaskStage.type, singleTaskStage.type, stageWithParallelBranches.type))
      }

      it("builds stages that will run in parallel") {
        assertThat(pipeline.stages.flatMap { it.requisiteStageRefIds })
          .isEmpty()
        // TODO: contexts, etc.
      }

      it("renames each parallel branch") {
        val stage = pipeline.stageByRef("1")
        assertThat(pipeline.stages.filter { it.parentStageId == stage.id }.map { it.name }).isEqualTo(listOf("run in us-east-1", "run in us-west-2", "run in eu-west-1"))
      }

      it("runs the parallel stages") {
        verify(queue, times(3)).push(check<StartStage> {
          assertThat(pipeline.stageById(it.stageId).parentStageId).isEqualTo(message.stageId)
        })
      }
    }

    context("when one branch starts") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "parallel"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildBeforeStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stages[0].id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("builds tasks for the branch") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(stage.tasks).isNotEmpty
        assertThat(stage.tasks.map(Task::getName)).isEqualTo(listOf("dummy"))
      }

      it("does not build more synthetic stages") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(pipeline.stages.mapNotNull(Stage::getParentStageId))
          .doesNotContain(stage.id)
      }
    }
  }

  describe("running a rolling push stage") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1"
        type = rollingPushStage.type
      }
    }

    context("when the stage starts") {
      val message = StartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("builds tasks for the main branch") {
        pipeline.stageById(message.stageId).let { stage ->
          assertThat(stage.tasks.size).isEqualTo(5)
          assertThat(stage.tasks[0].isLoopStart).isEqualTo(false)
          assertThat(stage.tasks[1].isLoopStart).isEqualTo(true)
          assertThat(stage.tasks[2].isLoopStart).isEqualTo(false)
          assertThat(stage.tasks[3].isLoopStart).isEqualTo(false)
          assertThat(stage.tasks[4].isLoopStart).isEqualTo(false)
          assertThat(stage.tasks[0].isLoopEnd).isEqualTo(false)
          assertThat(stage.tasks[1].isLoopEnd).isEqualTo(false)
          assertThat(stage.tasks[2].isLoopEnd).isEqualTo(false)
          assertThat(stage.tasks[3].isLoopEnd).isEqualTo(true)
          assertThat(stage.tasks[4].isLoopEnd).isEqualTo(false)
        }
      }

      it("runs the parallel stages") {
        verify(queue).push(check<StartTask> {
          assertThat(it.taskId).isEqualTo("1")
        })
      }
    }
  }

  describe("running an optional stage") {
    given("the stage should be run") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithSyntheticBefore.type
          context["stageEnabled"] = mapOf(
            "type" to "expression",
            "expression" to "true"
          )
        }
      }
      val message = StartStage(pipeline.stages.first())

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("proceeds with the first synthetic stage as normal") {
        verify(queue).push(any<StartStage>())
      }
    }

    given("the stage should be skipped") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithSyntheticBefore.type
          context["stageEnabled"] = mapOf(
            "type" to "expression",
            "expression" to "false"
          )
        }
      }
      val message = StartStage(pipeline.stages.first())

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("skips the stage") {
        verify(queue).push(isA<SkipStage>())
      }

      it("doesn't build any tasks") {
        assertThat(pipeline.stageById(message.stageId).tasks).isEmpty()
      }

      it("doesn't build any synthetic stages") {
        assertThat(pipeline.stages.filter { it.parentStageId == message.stageId })
          .isEmpty()
      }
    }

    given("the stage's optionality is a nested condition") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          name = "Preceding"
          status = FAILED_CONTINUE
        }
        stage {
          refId = "2"
          type = stageWithSyntheticBefore.type
          context["stageEnabled"] = mapOf(
            "type" to "expression",
            "expression" to "execution.stages.?[name == 'Preceding'][0]['status'].toString() != \"SUCCEEDED\""
          )
        }
      }
      val message = StartStage(pipeline.stageByRef("2"))

      and("the stage should be run") {
        beforeGroup {
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("proceeds with the first synthetic stage as normal") {
          verify(queue).push(any<StartStage>())
        }
      }

      and("the stage should be skipped") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            refId = "1"
            name = "Preceding"
            status = SUCCEEDED
          }
          stage {
            refId = "2"
            type = stageWithSyntheticBefore.type
            context["stageEnabled"] = mapOf(
              "type" to "expression",
              "expression" to "execution.stages.?[name == 'Preceding'][0]['status'].toString() != \"SUCCEEDED\""
            )
          }
        }
        val message = StartStage(pipeline.stageByRef("2"))

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving $message") {
          subject.handle(message)
        }

        it("skips the stage") {
          verify(queue).push(isA<SkipStage>())
        }
      }
    }
  }

  describe("invalid commands") {

    val message = StartStage(PIPELINE, "1", "foo", "1")

    given("no such execution") {
      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doThrow ExecutionNotFoundException("No Pipeline found for ${message.executionId}")
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("emits an error event") {
        verify(queue).push(isA<InvalidExecutionId>())
      }
    }

    given("no such stage") {
      val pipeline = pipeline {
        id = message.executionId
        application = "foo"
      }

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving a message") {
        subject.handle(message)
      }

      it("emits an error event") {
        verify(queue).push(isA<InvalidStageId>())
      }
    }
  }
})
