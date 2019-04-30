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
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.fixture.task
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails
import com.netflix.spinnaker.orca.pipeline.model.Stage.STAGE_TIMEOUT_OVERRIDE_KEY
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.threeten.extra.Minutes
import java.time.Duration
import kotlin.reflect.jvm.jvmName

object RunTaskHandlerTest : SubjectSpek<RunTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val stageNavigator: StageNavigator = mock()
  val task: DummyTask = mock()
  val timeoutOverrideTask: DummyTimeoutOverrideTask = mock()
  val exceptionHandler: ExceptionHandler = mock()
  val clock = fixedClock()
  val contextParameterProcessor = ContextParameterProcessor()

  subject(GROUP) {
    RunTaskHandler(
      queue,
      repository,
      stageNavigator,
      contextParameterProcessor,
      listOf(task, timeoutOverrideTask),
      clock,
      listOf(exceptionHandler),
      emptyList(),
      NoopRegistry()
    )
  }

  fun resetMocks() = reset(queue, repository, task, timeoutOverrideTask, exceptionHandler)

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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      and("has no context updates outputs") {
        val taskResult = TaskResult.SUCCEEDED

        beforeGroup {
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
          verify(queue).push(check<CompleteTask> {
            assertThat(it.status).isEqualTo(SUCCEEDED)
          })
        }

        it("does not update the stage or global context") {
          verify(repository, never()).storeStage(any())
        }
      }

      and("has context updates") {
        val stageOutputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).context(stageOutputs).build()

        beforeGroup {
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage context") {
          verify(repository).storeStage(check {
            assertThat(stageOutputs).isEqualTo(it.context)
          })
        }
      }

      and("has outputs") {
        val outputs = mapOf("foo" to "covfefe")
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("updates the stage outputs") {
          verify(repository).storeStage(check {
            assertThat(it.outputs).isEqualTo(outputs)
          })
        }
      }

      and("outputs a stageTimeoutMs value") {
        val outputs = mapOf(
          "foo" to "covfefe",
          "stageTimeoutMs" to Long.MAX_VALUE
        )
        val taskResult = TaskResult.builder(SUCCEEDED).outputs(outputs).build()

        beforeGroup {
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("does not write stageTimeoutMs to outputs") {
          verify(repository).storeStage(check {
            assertThat(it.outputs)
              .containsKey("foo")
              .doesNotContainKey("stageTimeoutMs")
          })
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
      val taskResult = TaskResult.RUNNING
      val taskBackoffMs = 30_000L

      beforeGroup {
        whenever(task.execute(any())) doReturn taskResult
        whenever(task.getDynamicBackoffPeriod(any(), any())) doReturn taskBackoffMs
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
        val taskResult = TaskResult.ofStatus(taskStatus)

        and("no overrides are in place") {
          beforeGroup {
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task $taskStatus") {
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(taskStatus)
            })
          }
        }

        and("the task should not fail the whole pipeline, only the branch") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
            }

            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task STOPPED") {
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(STOPPED)
            })
          }
        }

        and("the task should allow the pipeline to proceed") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = true
            }

            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)
          afterGroup { pipeline.stages.first().context.clear() }

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task FAILED_CONTINUE") {
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(FAILED_CONTINUE)
            })
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      and("it is not recoverable") {
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.responseDetails("o noes"),
          false
        )

        and("the task should fail the pipeline") {
          beforeGroup {
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
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(TERMINAL)
            })
          }

          it("attaches the exception to the stage context") {
            verify(repository).storeStage(check {
              assertThat(it.context["exception"]).isEqualTo(exceptionDetails)
            })
          }
        }

        and("the task should not fail the whole pipeline, only the branch") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
            }

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
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(STOPPED)
            })
          }
        }

        and("the task should allow the pipeline to proceed") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = true
            }

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
            verify(queue).push(check<CompleteTask> {
              assertThat(it.status).isEqualTo(FAILED_CONTINUE)
            })
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("emits an event indicating that the task was canceled") {
        verify(queue).push(CompleteTask(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          message.taskId,
          CANCELED,
          CANCELED
        ))
      }

      it("does not execute the task") {
        verifyZeroInteractions(task)
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the task as canceled") {
        verify(queue).push(CompleteTask(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          message.taskId,
          CANCELED,
          CANCELED
        ))
      }

      it("it tries to cancel the task") {
        verify(task).onCancel(any())
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
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
        verifyZeroInteractions(task)
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task as failed but continue") {
          verify(queue).push(CompleteTask(message, FAILED_CONTINUE))
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
        val message = RunTask(pipeline.type, pipeline.id, pipeline.application, pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          whenever(task.timeout) doReturn timeout.toMillis()
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
            val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

            beforeGroup {
              whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
              whenever(task.timeout) doReturn timeout.toMillis()
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
              val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

              beforeGroup {
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.timeout) doReturn timeout.toMillis()
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
              val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

              beforeGroup {
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.timeout) doReturn timeout.toMillis()
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
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.timeout) doReturn timeout.toMillis()
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
                whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
                whenever(task.timeout) doReturn timeout.toMillis()
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
                startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli() //started 5.1 minutes ago
                task {
                  id = "1"
                  implementingClass = DummyTimeoutOverrideTask::class.jvmName
                  status = RUNNING
                  startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli() //started 5.1 minutes ago
                }
              }
            }
            val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTimeoutOverrideTask::class.java)

            beforeGroup {
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
        val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("parses the expression") {
          verify(task).execute(check {
            assertThat(it.context["expr"]).isEqualTo(expected)
          })
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
              trigger = DefaultTrigger ("manual")
              task {
                id = "1"
                startTime = clock.instant().toEpochMilli()
              }
            }
          }
          val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

          beforeGroup {
            whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("evaluates the expression") {
            verify(task).execute(check {
              assertThat(it.context["expr"]).isEqualTo(expected)
            })
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("2").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("resolves deployed server groups") {
        verify(task).execute(check {
          assertThat(it.context["command"]).isEqualTo("serverGroupDetails.groovy mgmttest us-west-1 spindemo-test-v008")
        })
      }
    }

    given("parameters in the context and in the pipeline") {
      val pipeline = pipeline {
        trigger = DefaultTrigger(type = "manual", parameters = mapOf("dummy" to "foo"))
        stage {
          refId = "1"
          type = "jenkins"
          context["parameters"] = mapOf(
            "message" to "o hai"
          )
          task {
            id = "1"
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("does not overwrite the stage's parameters with the pipeline's") {
        verify(task).execute(check {
          assertThat(it.context["parameters"]).isEqualTo(mapOf("message" to "o hai"))
        })
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("2").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("passes the decoded expression to the task") {
        verify(task).execute(check {
          assertThat(it.context["expression"]).isEqualTo("bar")
        })
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
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("does not run any tasks") {
      verifyZeroInteractions(task)
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
      val message = RunTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

      beforeGroup {
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
})
