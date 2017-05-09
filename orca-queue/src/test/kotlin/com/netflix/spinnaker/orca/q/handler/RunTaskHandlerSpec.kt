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
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.subject.SubjectSpek
import org.threeten.extra.Minutes
import java.lang.RuntimeException
import java.time.Duration

object RunTaskHandlerSpec : SubjectSpek<RunTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val task: DummyTask = mock()
  val exceptionHandler: ExceptionHandler<in Exception> = mock()
  val clock = fixedClock()

  subject {
    RunTaskHandler(queue, repository, listOf(task), clock, listOf(exceptionHandler))
  }

  fun resetMocks() = reset(queue, repository, task, exceptionHandler)

  describe("running a task") {

    describe("that completes successfully") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          startTime = clock.instant().toEpochMilli()
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
      val taskResult = TaskResult(SUCCEEDED)

      beforeGroup {
        whenever(task.execute(any<Stage<*>>())) doReturn taskResult
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
          it.status shouldEqual SUCCEEDED
        })
      }
    }

    describe("that is not yet complete") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          startTime = clock.instant().toEpochMilli()
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
      val taskResult = TaskResult(RUNNING)
      val taskBackoffMs = 30_000L

      beforeGroup {
        whenever(task.execute(any())) doReturn taskResult
        whenever(task.backoffPeriod) doReturn taskBackoffMs
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("re-queues the command") {
        verify(queue).push(message, Duration.ofMillis(taskBackoffMs))
      }
    }

    describe("that fails") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = "whatever"
          startTime = clock.instant().toEpochMilli()
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
      val taskResult = TaskResult(TERMINAL)

      and("no overrides are in place") {
        beforeGroup {
          whenever(task.execute(any())) doReturn taskResult
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task TERMINAL") {
          verify(queue).push(check<CompleteTask> {
            it.status shouldEqual TERMINAL
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
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)
        afterGroup { pipeline.stages.first().context.clear() }

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task STOPPED") {
          verify(queue).push(check<CompleteTask> {
            it.status shouldEqual STOPPED
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
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)
        afterGroup { pipeline.stages.first().context.clear() }

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task FAILED_CONTINUE") {
          verify(queue).push(check<CompleteTask> {
            it.status shouldEqual FAILED_CONTINUE
          })
        }
      }
    }

    describe("that throws an exception") {
      val pipeline = pipeline {
        stage {
          type = "whatever"
          startTime = clock.instant().toEpochMilli()
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      context("that is not recoverable") {
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.ResponseDetails("o noes"),
          false
        )

        beforeGroup {
          whenever(task.execute(any())) doThrow RuntimeException("o noes")
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
          whenever(exceptionHandler.handles(any())) doReturn true
          whenever(exceptionHandler.handle(anyOrNull(), any())) doReturn exceptionDetails
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("marks the task as terminal") {
          verify(queue).push(check<CompleteTask> {
            it.status shouldEqual TERMINAL
          })
        }

        it("attaches the exception to the stage context") {
          verify(repository).storeStage(check {
            it.getContext()["exception"] shouldEqual exceptionDetails
          })
        }
      }

      context("that is recoverable") {
        val taskBackoffMs = 30_000L
        val exceptionDetails = ExceptionHandler.Response(
          RuntimeException::class.qualifiedName,
          "o noes",
          ExceptionHandler.ResponseDetails("o noes"),
          true
        )

        beforeGroup {
          whenever(task.backoffPeriod) doReturn taskBackoffMs
          whenever(task.execute(any())) doThrow RuntimeException("o noes")
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
          CANCELED
        ))
      }

      it("does not execute the task") {
        verifyZeroInteractions(task)
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
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
      context("the execution was never paused") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

      context("the execution had been paused") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          paused = PausedDetails().apply {
            pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
            resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
          }
          stage {
            type = "whatever"
            startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

      context("the execution had been paused but is timed out anyway") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          paused = PausedDetails().apply {
            pauseTime = clock.instant().minus(Minutes.of(3)).toEpochMilli()
            resumeTime = clock.instant().minus(Minutes.of(2)).toEpochMilli()
          }
          stage {
            type = "whatever"
            startTime = clock.instant().minusMillis(timeout.plusMinutes(1).toMillis() + 1).toEpochMilli()
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

      context("the execution spent a long time running before stages") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = SUCCEEDED
              startTime = clock.instant().minusMillis(timeout.toMillis() - 1).toEpochMilli()
            }
            task {
              id = "2"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "2", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

      context("the execution spent a long time running before stages but is timed out anyway") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            startTime = clock.instant().minusMillis(timeout.toMillis() + 2).toEpochMilli()
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = SUCCEEDED
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
            task {
              id = "2"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

    describe("the context passed to the task") {
      val pipeline = pipeline {
        context["global"] = "foo"
        context["override"] = "global"
        stage {
          type = "whatever"
          startTime = clock.instant().toEpochMilli()
          context["stage"] = "foo"
          context["override"] = "stage"
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
      val taskResult = TaskResult(SUCCEEDED)

      beforeGroup {
        whenever(task.execute(any<Stage<*>>())) doReturn taskResult
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("merges stage and global contexts") {
        verify(task).execute(check {
          it.getContext() shouldEqual mapOf(
            "global" to "foo",
            "stage" to "foo",
            "override" to "stage"
          )
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
          implementingClass = InvalidTask::class.qualifiedName
        }
      }
    }
    val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", InvalidTask::class.java)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
})
