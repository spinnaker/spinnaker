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
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.DefaultStageResolver
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.STOPPED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution.PausedDetails
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.api.test.task
import com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl.STAGE_TIMEOUT_OVERRIDE_KEY
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.DummyTask
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.orca.q.DummyCloudProviderAwareTask
import com.netflix.spinnaker.orca.q.DummyTimeoutOverrideTask
import com.netflix.spinnaker.orca.q.TasksProvider
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.PauseTask
import com.netflix.spinnaker.orca.q.InvalidTask
import com.netflix.spinnaker.orca.q.InvalidTaskType
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.collections.set
import kotlin.reflect.jvm.jvmName
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.spy
import org.threeten.extra.Minutes

object RunTaskHandlerTest : SubjectSpek<RunTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  val task: DummyTask = mock {
    on { extensionClass } doReturn DummyTask::class.java
    on { aliases() } doReturn emptyList<String>()
  }
  val cloudProviderAwareTask: DummyCloudProviderAwareTask = mock {
    on { extensionClass } doReturn DummyCloudProviderAwareTask::class.java
  }
  val timeoutOverrideTask: DummyTimeoutOverrideTask = mock {
    on { extensionClass } doReturn DummyTimeoutOverrideTask::class.java
    on { aliases() } doReturn emptyList<String>()
  }

  val tasks = mutableListOf(task, cloudProviderAwareTask, timeoutOverrideTask)
  val exceptionHandler: ExceptionHandler = mock()
  // Stages store times as ms-since-epoch, and we do a lot of tests to make sure things run at the
  // appropriate time, so we need to make sure
  // clock.instant() == Instant.ofEpochMilli(clock.instant())
  val clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneId.systemDefault())
  val contextParameterProcessor = ContextParameterProcessor()
  val dynamicConfigService: DynamicConfigService = mock()
  val taskExecutionInterceptors: List<TaskExecutionInterceptor> = listOf(mock())
  val stageResolver : DefaultStageResolver = mock()
  val manualJudgmentStage : ManualJudgmentStage = ManualJudgmentStage()
  val stageNavigator: StageNavigator = mock()



  subject(GROUP) {
    whenever(dynamicConfigService.getConfig(eq(Int::class.java), eq("tasks.warningInvocationTimeMs"), any())) doReturn 0

    RunTaskHandler(
      queue,
      repository,
      stageNavigator,
      DefaultStageDefinitionBuilderFactory(stageResolver),
      contextParameterProcessor,
      TaskResolver(TasksProvider(mutableListOf(task, timeoutOverrideTask, cloudProviderAwareTask) as Collection<Task>)),
      clock,
      listOf(exceptionHandler),
      taskExecutionInterceptors,
      NoopRegistry(),
      dynamicConfigService
    )
  }

  fun resetMocks() = reset(queue, repository, task, timeoutOverrideTask, cloudProviderAwareTask, exceptionHandler)

  describe("running a task") {

    describe("that completes successfully") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

      and("has no context updates outputs") {
        val taskResult = TaskResult.SUCCEEDED

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("executes the task") {
          verify(task).execute(pipeline.stages.first())
        }

        it("completes the task") {
          verify(queue).push(
            check<CompleteTask> {
              assertThat(it.status).isEqualTo(SUCCEEDED)
            }
          )
        }

        it("does not update the stage or global context") {
          verify(repository, never()).storeStage(any())
        }
      }

      and("has context updates") {
        val stageOutputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).context(stageOutputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage context") {
          verify(repository).storeStage(
            check {
              assertThat(stageOutputs).isEqualTo(it.context)
            }
          )
        }
      }

      and("has outputs") {
        val outputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage outputs") {
          verify(repository).storeStage(
            check {
              assertThat(it.outputs).isEqualTo(outputs)
            }
          )
        }
      }

      and("outputs a stageTimeoutMs value") {
        val outputs = mapOf(
          "foo" to "covfefe",
          "stageTimeoutMs" to Long.MAX_VALUE
        )
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("does not write stageTimeoutMs to outputs") {
          verify(repository).storeStage(
            check {
              assertThat(it.outputs)
                .containsKey("foo")
                .doesNotContainKey("stageTimeoutMs")
            }
          )
        }
      }
    }

    describe("that completes successfully prefering specified TaskExecution implementingClass") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
            implementingClass = task.javaClass.canonicalName
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", WaitTask::class.java)

      and("has no context updates outputs") {
        val taskResult = TaskResult.SUCCEEDED

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("executes the task") {
          verify(task).execute(pipeline.stages.first())
        }

        it("completes the task") {
          verify(queue).push(
            check<CompleteTask> {
              assertThat(it.status).isEqualTo(SUCCEEDED)
            }
          )
        }

        it("does not update the stage or global context") {
          verify(repository, never()).storeStage(any())
        }
      }

      and("has context updates") {
        val stageOutputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).context(stageOutputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage context") {
          verify(repository).storeStage(
            check {
              assertThat(stageOutputs).isEqualTo(it.context)
            }
          )
        }
      }

      and("has outputs") {
        val outputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage outputs") {
          verify(repository).storeStage(
            check {
              assertThat(it.outputs).isEqualTo(outputs)
            }
          )
        }
      }

      and("outputs a stageTimeoutMs value") {
        val outputs = mapOf(
          "foo" to "covfefe",
          "stageTimeoutMs" to Long.MAX_VALUE
        )
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("does not write stageTimeoutMs to outputs") {
          verify(repository).storeStage(
            check {
              assertThat(it.outputs)
                .containsKey("foo")
                .doesNotContainKey("stageTimeoutMs")
            }
          )
        }
      }
    }

    describe("that is not yet complete") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)
      val taskResult = TaskResult.RUNNING
      val taskBackoffMs = 30_000L
      val maxBackOffLimit = 120_000L

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.maxTaskBackoff()) doReturn maxBackOffLimit }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
        whenever(task.execute(any())) doReturn taskResult
        whenever(task.getDynamicBackoffPeriod(any(), any())) doReturn taskBackoffMs
        whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("tasks.global.backOffPeriod"), any())) doReturn 0L
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("re-queues the command") {
        verify(queue).push(message, Duration.ofMillis(taskBackoffMs))
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      describe("that fails with $taskStatus") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = "whatever"
            task {
              id = "1"
              startTime = clock.instant().toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)
        val taskResult = TaskResult.ofStatus(taskStatus)

        and("no overrides are in place") {
          beforeGroup {
            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
            taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task $taskStatus") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(taskStatus)
              }
            )
          }
        }

        and("the task should not fail the whole pipeline, only the branch") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
            }
            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task STOPPED") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(STOPPED)
              }
            )
          }
        }

        and("the task should allow the pipeline to proceed") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = true
            }
            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task FAILED_CONTINUE") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(FAILED_CONTINUE)
              }
            )
          }
        }
      }
    }

    describe("that throws an exception") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)
      val taskResult = TaskResult.ofStatus(TERMINAL)

      and("it is not recoverable") {
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.responseDetails("o noes"),
          false
        )

        and("the task should fail the pipeline") {
          beforeGroup {
            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
            taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doThrow RuntimeException("o noes") }
            whenever(task.execute(any())) doThrow RuntimeException("o noes")
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task as terminal") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(TERMINAL)
              }
            )
          }

          it("attaches the exception to the stage context") {
            verify(repository).storeStage(
              check {
                assertThat(it.context["exception"]).isEqualTo(exceptionDetails)
              }
            )
          }
        }

        and("the task should not fail the whole pipeline, only the branch") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
            }

            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            whenever(task.execute(any())) doThrow RuntimeException("o noes")
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task STOPPED") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(STOPPED)
              }
            )
          }
        }

        and("the task should allow the pipeline to proceed") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = true
            }

            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            whenever(task.execute(any())) doThrow RuntimeException("o noes")
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            whenever(exceptionHandler.handles(any())) doReturn true
            whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task FAILED_CONTINUE") {
            verify(queue).push(
              check<CompleteTask> {
                assertThat(it.status).isEqualTo(FAILED_CONTINUE)
              }
            )
          }
        }
      }

      and("it is recoverable") {
        val taskBackoffMs = 30_000L
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.responseDetails("o noes"),
          true
        )

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          whenever(task.getDynamicBackoffPeriod(any(), any())) doReturn taskBackoffMs
          whenever(task.execute(any())) doThrow RuntimeException("o noes")
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(exceptionHandler.handles(any())) doReturn true
          whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("re-runs the task") {
          verify(queue).push(eq(message), eq(Duration.ofMillis(taskBackoffMs)))
        }
      }
    }

    describe("when the execution has stopped") {
      val pipeline = pipeline {
        status = TERMINAL
        stage {
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("emits an event indicating that the task was canceled") {
        verify(queue).push(
          CompleteTask(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId,
            message.taskId,
            CANCELED,
            CANCELED
          )
        )
      }

      it("does not execute the task") {
        verify(task, times(1)).aliases()
        verify(task, times(3)).extensionClass
        verifyNoMoreInteractions(task)
      }
    }

    describe("when the execution has been canceled") {
      val pipeline = pipeline {
        status = RUNNING
        isCanceled = true
        stage {
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the task as canceled") {
        verify(queue).push(
          CompleteTask(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId,
            message.taskId,
            CANCELED,
            CANCELED
          )
        )
      }

      it("it tries to cancel the task") {
        verify(task).onCancelWithResult(any())
      }
    }

    describe("when the execution has been paused") {
      val pipeline = pipeline {
        status = PAUSED
        stage {
          type = "whatever"
          status = RUNNING
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the task as paused") {
        verify(queue).push(PauseTask(message))
      }

      it("does not execute the task") {
        verify(task, times(1)).aliases()
        verify(task, times(3)).extensionClass
        verifyNoMoreInteractions(task)
      }
    }

    describe("when the task has exceeded its timeout") {
      given("the execution was never paused") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("fails the task") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the execution was marked to continue") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
            context["failPipeline"] = false
            context["continuePipeline"] = true
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task as failed but continue") {
          verify(queue).push(CompleteTask(message, FAILED_CONTINUE, TERMINAL))
        }
      }

      given("the execution is marked to succeed on timeout") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            context = hashMapOf<String, Any>("markSuccessfulOnTimeout" to true)
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task as succeeded") {
          verify(queue).push(CompleteTask(message, SUCCEEDED))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the execution had been paused") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          paused = PausedDetails().apply {
            pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
            resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
          }
          stage {
            type = "whatever"
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("executes the task") {
          verify(task).execute(any())
        }
      }

      given("the execution had been paused but is timed out anyway") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          paused = PausedDetails().apply {
            pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
            resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
          }
          stage {
            type = "whatever"
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.plusMinutes(1).toMillis() + 1).toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("fails the task") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the execution had been paused but only before this task started running") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          paused = PausedDetails().apply {
            pauseTime = clock.instant().minus(Minutes.of(10)).toEpochMilli()
            resumeTime = clock.instant().minus(Minutes.of(9)).toEpochMilli()
          }
          stage {
            type = "whatever"
            task {
              id = "1"
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("fails the task") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the stage waited for an execution window") {
        val timeout = Duration.ofMinutes(5)
        val windowDuration = Duration.ofHours(1)
        val windowStartedAt = clock.instant().minus(timeout).minus(windowDuration).plusSeconds(1)
        val windowEndedAt = clock.instant().minus(timeout).plusSeconds(1)
        val pipeline = pipeline {
          stage {
            stage {
              type = RestrictExecutionDuringTimeWindow.TYPE
              status = SUCCEEDED
              startTime = windowStartedAt.toEpochMilli()
              endTime = windowEndedAt.toEpochMilli()
            }
            refId = "1"
            type = "whatever"
            context[STAGE_TIMEOUT_OVERRIDE_KEY] = timeout.toMillis()
            startTime = windowStartedAt.toEpochMilli()
            task {
              id = "1"
              startTime = windowEndedAt.toEpochMilli()
              status = RUNNING
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, pipeline.application, stage.id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("executes the task") {
          verify(task).execute(pipeline.stageByRef("1"))
        }
      }

      given("the stage waited for an execution window but is timed out anyway") {
        val timeout = Duration.ofMinutes(5)
        val windowDuration = Duration.ofHours(1)
        val windowStartedAt = clock.instant().minus(timeout).minus(windowDuration).minusSeconds(1)
        val windowEndedAt = clock.instant().minus(timeout).minusSeconds(1)
        val pipeline = pipeline {
          stage {
            stage {
              type = RestrictExecutionDuringTimeWindow.TYPE
              status = SUCCEEDED
              startTime = windowStartedAt.toEpochMilli()
              endTime = windowEndedAt.toEpochMilli()
            }
            refId = "1"
            type = "whatever"
            context[STAGE_TIMEOUT_OVERRIDE_KEY] = timeout.toMillis()
            startTime = windowStartedAt.toEpochMilli()
            task {
              id = "1"
              startTime = windowEndedAt.toEpochMilli()
              status = RUNNING
            }
          }
        }
        val message = RunTask(pipeline.type, pipeline.id, pipeline.application, pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("fails the task") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }
    }

    describe("handles onTimeout responses") {
      val timeout = Duration.ofMinutes(5)
      val pipeline = pipeline {
        stage {
          type = "whatever"
          task {
            id = "1"
            status = RUNNING
            startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

      given("the task returns fail_continue") {
        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
          whenever(task.onTimeout(any())) doReturn TaskResult.ofStatus(FAILED_CONTINUE)
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task fail_continue") {
          verify(queue).push(CompleteTask(message, FAILED_CONTINUE))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the task returns terminal") {
        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
          whenever(task.onTimeout(any())) doReturn TaskResult.ofStatus(TERMINAL)
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task terminal") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }

      given("the task returns succeeded") {
        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
          whenever(task.onTimeout(any())) doReturn TaskResult.ofStatus(SUCCEEDED)
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
          subject
        }

        it("marks the task terminal") {
          verify(queue).push(CompleteTask(message, TERMINAL))
        }

        it("does not execute the task") {
          verify(task, never()).execute(any())
        }
      }
    }

    given("there is a timeout override") {
      val timeout = Duration.ofMinutes(5)
      val timeoutOverride = Duration.ofMinutes(10)

      timeoutOverride.toMillis().let { listOf(it.toInt(), it, it.toDouble()) }.forEach { stageTimeoutMs ->
        and("the override is a ${stageTimeoutMs.javaClass.simpleName}") {
          and("the stage is between the default and overridden duration") {
            val pipeline = pipeline {
              stage {
                type = "whatever"
                context["stageTimeoutMs"] = stageTimeoutMs
                startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
                task {
                  id = "1"
                  status = RUNNING
                  startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
                }
              }
            }
            val stage = pipeline.stages.first()
            val taskResult = TaskResult.RUNNING
            val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

            beforeGroup {
              tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
              taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
              taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
              whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
              whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
            }

            afterGroup(::resetMocks)

            on("receiving $message") {
              subject.handle(message)
            }

            it("executes the task") {
              verify(task).execute(any())
            }
          }

          and("the timeout override has been exceeded by stage") {
            and("the stage has never been paused") {
              val pipeline = pipeline {
                stage {
                  type = "whatever"
                  context["stageTimeoutMs"] = stageTimeoutMs
                  startTime = clock.instant().minusMillis(timeoutOverride.toMillis() + 1).toEpochMilli()
                  task {
                    id = "1"
                    status = RUNNING
                    startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
                  }
                }
              }
              val stage = pipeline.stages.first()
              val taskResult = TaskResult.RUNNING
              val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

              beforeGroup {
                tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
                taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
                taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
              }

              afterGroup(::resetMocks)

              on("receiving $message") {
                subject.handle(message)
              }

              it("fails the task") {
                verify(queue).push(CompleteTask(message, TERMINAL))
              }

              it("does not execute the task") {
                verify(task, never()).execute(any())
              }
            }

            and("the execution had been paused") {
              val pipeline = pipeline {
                paused = PausedDetails().apply {
                  pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
                  resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
                }
                stage {
                  type = "whatever"
                  context["stageTimeoutMs"] = stageTimeoutMs
                  startTime = clock.instant().minusMillis(timeoutOverride.toMillis() + 1).toEpochMilli()
                  task {
                    id = "1"
                    status = RUNNING
                    startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
                  }
                }
              }
              val stage = pipeline.stages.first()
              val taskResult = TaskResult.RUNNING
              val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

              beforeGroup {
                tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
                taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
                taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
              }

              afterGroup(::resetMocks)

              action("the handler receives a message") {
                subject.handle(message)
              }

              it("executes the task") {
                verify(task).execute(any())
              }
            }

            and("the execution had been paused but is timed out anyway") {
              val pipeline = pipeline {
                paused = PausedDetails().apply {
                  pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
                  resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
                }
                stage {
                  type = "whatever"
                  context["stageTimeoutMs"] = stageTimeoutMs
                  startTime = clock.instant().minusMillis(timeoutOverride.plusMinutes(1).toMillis() + 1).toEpochMilli()
                  task {
                    id = "1"
                    status = RUNNING
                    startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
                  }
                }
              }
              val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

              beforeGroup {
                tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
              }

              afterGroup(::resetMocks)

              action("the handler receives a message") {
                subject.handle(message)
              }

              it("fails the task") {
                verify(queue).push(CompleteTask(message, TERMINAL))
              }

              it("does not execute the task") {
                verify(task, never()).execute(any())
              }
            }

            and("the execution had been paused but only before this stage started running") {
              val pipeline = pipeline {
                paused = PausedDetails().apply {
                  pauseTime = clock.instant().minus(Minutes.of(15)).toEpochMilli()
                  resumeTime = clock.instant().minus(Minutes.of(14)).toEpochMilli()
                }
                stage {
                  type = "whatever"
                  context["stageTimeoutMs"] = stageTimeoutMs
                  startTime = clock.instant().minusMillis(timeoutOverride.toMillis() + 1).toEpochMilli()
                  task {
                    id = "1"
                    status = RUNNING
                    startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
                  }
                }
              }
              val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

              beforeGroup {
                tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
              }

              afterGroup(::resetMocks)

              action("the handler receives a message") {
                subject.handle(message)
              }

              it("fails the task") {
                verify(queue).push(CompleteTask(message, TERMINAL))
              }

              it("does not execute the task") {
                verify(task, never()).execute(any())
              }
            }
          }

          and("the task is an overridabletimeout task that shouldn't time out") {
            val pipeline = pipeline {
              stage {
                type = "whatever"
                context["stageTimeoutMs"] = stageTimeoutMs
                startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli() // started 5.1 minutes ago
                task {
                  id = "1"
                  implementingClass = DummyTimeoutOverrideTask::class.jvmName
                  status = RUNNING
                  startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli() // started 5.1 minutes ago
                }
              }
            }
            val stage = pipeline.stages.first()
            val taskResult = TaskResult.RUNNING
            val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTimeoutOverrideTask::class.java)

            beforeGroup {
              tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
              taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(timeoutOverrideTask, stage)) doReturn stage }
              taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(timeoutOverrideTask, stage, taskResult)) doReturn taskResult }
              whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
              whenever(timeoutOverrideTask.timeout) doReturn timeout.toMillis()
            }

            afterGroup(::resetMocks)

            on("receiving $message") {
              subject.handle(message)
            }

            it("executes the task") {
              verify(timeoutOverrideTask).execute(any())
            }
          }
        }
      }
    }
  }

  describe("expressions in the context") {
    mapOf(
      "\${1 == 2}" to false,
      "\${1 == 1}" to true,
      mapOf("key" to "\${1 == 2}") to mapOf("key" to false),
      mapOf("key" to "\${1 == 1}") to mapOf("key" to true),
      mapOf("key" to mapOf("key" to "\${1 == 2}")) to mapOf("key" to mapOf("key" to false)),
      mapOf("key" to mapOf("key" to "\${1 == 1}")) to mapOf("key" to mapOf("key" to true))
    ).forEach { expression, expected ->
      given("an expression $expression in the stage context") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = "whatever"
            context["expr"] = expression
            task {
              id = "1"
              startTime = clock.instant().toEpochMilli()
            }
          }
        }
        val stage = pipeline.stages.first()
        val taskResult = TaskResult.SUCCEEDED
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("parses the expression") {
          verify(task).execute(
            check {
              assertThat(it.context["expr"]).isEqualTo(expected)
            }
          )
        }
      }
    }

    describe("can reference non-existent trigger props") {
      mapOf(
        "\${trigger.type == 'manual'}" to true,
        "\${trigger.buildNumber == null}" to true,
        "\${trigger.quax ?: 'no quax'}" to "no quax"
      ).forEach { expression, expected ->
        given("an expression $expression in the stage context") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = "whatever"
              context["expr"] = expression
              trigger = DefaultTrigger("manual")
              task {
                id = "1"
                startTime = clock.instant().toEpochMilli()
              }
            }
          }
          val stage = pipeline.stages.first()
          val taskResult = TaskResult.SUCCEEDED
          val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

          beforeGroup {
            tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
            taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
            taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("evaluates the expression") {
            verify(task).execute(
              check {
                assertThat(it.context["expr"]).isEqualTo(expected)
              }
            )
          }
        }
      }
    }

    given("a reference to deployedServerGroups in the stage context") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = "createServerGroup"
          context = mapOf(
            "deploy.server.groups" to mapOf(
              "us-west-1" to listOf(
                "spindemo-test-v008"
              )
            ),
            "account" to "mgmttest",
            "region" to "us-west-1"
          )
          status = SUCCEEDED
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          type = "whatever"
          context["command"] = "serverGroupDetails.groovy \${ deployedServerGroups[0].account } \${ deployedServerGroups[0].region } \${ deployedServerGroups[0].serverGroup }"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stageByRef("2")
      val taskResult = TaskResult.SUCCEEDED
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("2").id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
        whenever(task.execute(any())) doReturn taskResult
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("resolves deployed server groups") {
        verify(task).execute(
          check {
            assertThat(it.context["command"]).isEqualTo("serverGroupDetails.groovy mgmttest us-west-1 spindemo-test-v008")
          }
        )
      }
    }

    given("parameters in the context and in the pipeline") {
      val pipeline = pipeline {
        trigger = DefaultTrigger(type = "manual", parameters = mutableMapOf("dummy" to "foo"))
        stage {
          refId = "1"
          type = "jenkins"
          context["parameters"] = mutableMapOf(
            "message" to "o hai"
          )
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stages.first()
      val taskResult = TaskResult.SUCCEEDED
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
        whenever(task.execute(any())) doReturn taskResult
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("does not overwrite the stage's parameters with the pipeline's") {
        verify(task).execute(
          check {
            assertThat(it.context["parameters"]).isEqualTo(mapOf("message" to "o hai"))
          }
        )
      }
    }

    given("an expression in the context that refers to a prior stage output") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          outputs["foo"] = "bar"
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          context["expression"] = "\${foo}"
          type = "whatever"
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val stage = pipeline.stageByRef("2")
      val taskResult = TaskResult.SUCCEEDED
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
        whenever(task.execute(any())) doReturn taskResult
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("passes the decoded expression to the task") {
        verify(task).execute(
          check {
            assertThat(it.context["expression"]).isEqualTo("bar")
          }
        )
      }
    }
  }

  describe("no such task") {
    val pipeline = pipeline {
      stage {
        type = "whatever"
        task {
          id = "1"
          implementingClass = InvalidTask::class.jvmName
        }
      }
    }
    val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", InvalidTask::class.java)

    beforeGroup {
      tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("does not run any tasks") {
      verify(task, times(1)).aliases()
      verify(task, times(5)).extensionClass
      verifyNoMoreInteractions(task)
    }

    it("emits an error event") {
      verify(queue).push(isA<InvalidTaskType>())
    }
  }

  describe("manual skip behavior") {
    given("a stage with a manual skip flag") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
          task {
            id = "1"
            implementingClass = DummyTask::class.jvmName
          }
          context["manualSkip"] = true
        }
      }
      val stage = pipeline.stageByRef("1")
      val taskResult = TaskResult.SUCCEEDED
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)

      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(task, stage, taskResult)) doReturn taskResult }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("sets the task's status to SKIPPED and completes the task") {
        verify(queue).push(CompleteTask(message, SKIPPED))
      }
    }
  }

  describe("max configurable back off value") {
    setOf(
      BackOff(5_000L, 10_000L, 20_000L, 30_000L, 30_000L),
      BackOff(10_000L, 20_000L, 30_000L, 5_000L, 30_000L),
      BackOff(20_000L, 30_000L, 5_000L, 10_000L, 30_000L),
      BackOff(30_000L, 5_000L, 10_000L, 20_000L, 30_000L),
      BackOff(20_000L, 5_000L, 10_000L, 30_002L, 30_001L)
    ).forEach { backOff ->
      given("the back off values $backOff") {
        val pipeline = pipeline {
          stage {
            type = "whatever"
            task {
              id = "1"
              implementingClass = DummyCloudProviderAwareTask::class.jvmName
              startTime = clock.instant().toEpochMilli()
              context["cloudProvider"] = "aws"
              context["deploy.account.name"] = "someAccount"
            }
          }
        }
        val stage = pipeline.stages.first()
        val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyCloudProviderAwareTask::class.java)
        val taskResult = TaskResult.RUNNING

        beforeGroup {
          tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
          whenever(cloudProviderAwareTask.execute(any())) doReturn taskResult
          taskExecutionInterceptors.forEach { whenever(it.maxTaskBackoff()) doReturn backOff.limit }
          taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(cloudProviderAwareTask, stage)) doReturn stage }
          taskExecutionInterceptors.forEach { whenever(it.afterTaskExecution(cloudProviderAwareTask, stage, taskResult)) doReturn taskResult }

          whenever(cloudProviderAwareTask.getDynamicBackoffPeriod(any(), any())) doReturn backOff.taskBackOffMs
          whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("tasks.global.backOffPeriod"), any())) doReturn backOff.globalBackOffMs
          whenever(cloudProviderAwareTask.hasCloudProvider(any())) doReturn true
          whenever(cloudProviderAwareTask.getCloudProvider(any<StageExecution>())) doReturn "aws"
          whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("tasks.aws.backOffPeriod"), any())) doReturn backOff.cloudProviderBackOffMs
          whenever(cloudProviderAwareTask.hasCredentials(any())) doReturn true
          whenever(cloudProviderAwareTask.getCredentials(any<StageExecution>())) doReturn "someAccount"
          whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("tasks.aws.someAccount.backOffPeriod"), any())) doReturn backOff.accountBackOffMs

          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("selects the max value, unless max value is over limit ${backOff.limit} in which case limit is used") {
          verify(queue).push(message, Duration.ofMillis(backOff.expectedMaxBackOffMs))
        }
      }
    }
  }

  describe("when a previous stage was a skipped manual judgment stage with auth propagated") {
    given("a stage with a manual judgment type and auth propagated") {
      val timeout = Duration.ofMinutes(5)
      val lastModifiedUser = StageExecution.LastModifiedDetails()
      lastModifiedUser.user = "user"
      lastModifiedUser.allowedAccounts = listOf("user")

      val pipeline = pipeline {
        stage {
          refId = "1"
          type = manualJudgmentStage.type
          manualJudgmentStage.plan(this)
          status = SUCCEEDED
          context["propagateAuthenticationContext"] = true
          context["judgmentStatus"] = "continue"
          lastModified = lastModifiedUser
        }
      }

      val stageSkipped : StageExecution = stage {
        refId = "2"
        type = manualJudgmentStage.type
        manualJudgmentStage.plan(this)
        context["manualSkip"] = true
        status = SKIPPED
        requisiteStageRefIds = setOf("1")
      }

      val stageSpy = spy(stageSkipped)

      val stageResult1 : com.netflix.spinnaker.orca.pipeline.util.StageNavigator.Result = mock()
      whenever(stageResult1.stage) doReturn pipeline.stageByRef("1")
      whenever(stageResult1.stageBuilder) doReturn manualJudgmentStage
      val stageResult2 : com.netflix.spinnaker.orca.pipeline.util.StageNavigator.Result = mock()
      whenever(stageResult2.stage) doReturn stageSpy
      whenever(stageResult2.stageBuilder) doReturn manualJudgmentStage
      pipeline.stages.add(stageSpy)

      val stage = pipeline.stageByRef("2")
      val message = RunTask(pipeline.type, pipeline.id, "foo", stage.id, "1", DummyTask::class.java)


      beforeGroup {
        tasks.forEach { whenever(it.extensionClass) doReturn it::class.java }
        taskExecutionInterceptors.forEach { whenever(it.beforeTaskExecution(task, stage)) doReturn stage }
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        whenever(task.getDynamicTimeout(any())) doReturn timeout.toMillis()
        whenever(stageNavigator.ancestors(stage)) doReturn listOf(stageResult2, stageResult1)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the task as skipped") {
        verify(queue).push(CompleteTask(message, SKIPPED))
      }

      it("verify if last authenticated user was retrieved from candidate stage") {
        assertEquals(stageSpy.lastModified, lastModifiedUser)
      }

    }
  }
})

data class BackOff(
  val taskBackOffMs: Long,
  val globalBackOffMs: Long,
  val cloudProviderBackOffMs: Long,
  val accountBackOffMs: Long,
  val expectedMaxBackOffMs: Long,
  val limit: Long = 30_001L
)
