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
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.threeten.extra.Minutes
import java.lang.RuntimeException
import java.time.Duration

object RunTaskHandlerTest : SubjectSpek<RunTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val task: DummyTask = mock()
  val exceptionHandler: ExceptionHandler = mock()
  val clock = fixedClock()
  val contextParameterProcessor = ContextParameterProcessor()

  subject(GROUP) {
    RunTaskHandler(
      queue,
      repository,
      contextParameterProcessor,
      listOf(task),
      clock,
      listOf(exceptionHandler),
      NoopRegistry()
    )
  }

  fun resetMocks() = reset(queue, repository, task, exceptionHandler)

  describe("running a task") {

    describe("that completes successfully") {
      val pipeline = pipeline {
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
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
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

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      describe("that fails with $taskStatus") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = "whatever"
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              startTime = clock.instant().toEpochMilli()
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
        val taskResult = TaskResult(taskStatus)

        and("no overrides are in place") {
          beforeGroup {
            whenever(task.execute(any())) doReturn taskResult
            whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("marks the task $taskStatus") {
            verify(queue).push(check<CompleteTask> {
              it.status shouldEqual taskStatus
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
    }

    describe("that throws an exception") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = "whatever"
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

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

        and("the task should not fail the whole pipeline, only the branch") {
          beforeGroup {
            pipeline.stageByRef("1").apply {
              context["failPipeline"] = false
              context["continuePipeline"] = false
            }

            whenever(task.execute(any())) doThrow RuntimeException("o noes")
            whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

            whenever(task.execute(any())) doThrow RuntimeException("o noes")
            whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
              it.status shouldEqual FAILED_CONTINUE
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
      given("the execution was never paused") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
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

      given("the execution was marked to continue") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
            }
            context["failPipeline"] = false
            context["continuePipeline"] = true
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

        it("marks the task as failed but continue") {
          verify(queue).push(CompleteTask(message, FAILED_CONTINUE))
        }

      }

      given("the execution is marked to succeed on timeout") {
        val timeout = Duration.ofMinutes(5)
        val pipeline = pipeline {
          stage {
            type = "whatever"
            context = hashMapOf("markSuccessfulOnTimeout" to true) as Map<String, Any>?
            task {
              id = "1"
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
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
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
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
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.plusMinutes(1).toMillis() + 1).toEpochMilli()
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
              implementingClass = DummyTask::class.qualifiedName
              status = RUNNING
              startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
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

    given("there is a timeout override") {
      val timeout = Duration.ofMinutes(5)
      val timeoutOverride = Duration.ofMinutes(10)

      timeoutOverride.toMillis().let { listOf(it.toInt(), it, it.toDouble()) }.forEach { stageTimeoutMs ->
        and("the override is a ${stageTimeoutMs.javaClass.simpleName}") {
          and("the task is between the default and overridden duration") {
            val pipeline = pipeline {
              stage {
                type = "whatever"
                context["stageTimeoutMs"] = stageTimeoutMs
                task {
                  id = "1"
                  implementingClass = DummyTask::class.qualifiedName
                  status = RUNNING
                  startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
                }
              }
            }
            val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

            beforeGroup {
              whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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

          and("the timeout override has been exceeded") {
            val pipeline = pipeline {
              stage {
                type = "whatever"
                context["stageTimeoutMs"] = stageTimeoutMs
                task {
                  id = "1"
                  implementingClass = DummyTask::class.qualifiedName
                  status = RUNNING
                  startTime = clock.instant().minusMillis(timeoutOverride.toMillis() + 1).toEpochMilli()
                }
              }
            }
            val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

            beforeGroup {
              whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
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
              implementingClass = DummyTask::class.qualifiedName
              startTime = clock.instant().toEpochMilli()
            }
          }
        }
        val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

        beforeGroup {
          whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
          whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("parses the expression") {
          verify(task).execute(check {
            it.getContext()["expr"] shouldEqual expected
          })
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
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("2").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("resolves deployed server groups") {
        verify(task).execute(check {
          it.getContext()["command"] shouldEqual "serverGroupDetails.groovy mgmttest us-west-1 spindemo-test-v008"
        })
      }
    }

    given("parameters in the context and in the pipeline") {
      val pipeline = pipeline {
        trigger["parameters"] = mapOf(
          "dummy" to "foo"
        )
        stage {
          refId = "1"
          type = "jenkins"
          context["parameters"] = mapOf(
            "message" to "o hai"
          )
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("does not overwrite the stage's parameters with the pipeline's") {
        verify(task).execute(check {
          it.getContext()["parameters"] shouldEqual mapOf("message" to "o hai")
        })
      }
    }

    given("an expression in the context that refers to a global context value") {
      val pipeline = pipeline {
        context["foo"] = "bar"
        stage {
          refId = "1"
          context["expression"] = "\${foo}"
          type = "whatever"
          task {
            id = "1"
            implementingClass = DummyTask::class.qualifiedName
            startTime = clock.instant().toEpochMilli()
          }
        }
      }
      val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id, "1", DummyTask::class.java)

      beforeGroup {
        whenever(task.execute(any())) doReturn TaskResult.SUCCEEDED
        whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("passes the decoded expression to the task") {
        verify(task).execute(check {
          it.getContext()["expression"] shouldEqual "bar"
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

  describe("deduct the time spent throttled from the elapsed time") {

    val timeout = Duration.ofMinutes(5)
    val pipeline = pipeline {
      stage {
        type = "somethingFun"
        task {
          id = "1"
          implementingClass = DummyTask::class.qualifiedName
          startTime = clock.instant().minusMillis(timeout.toMillis() + 1).toEpochMilli()
          status = RUNNING
        }
      }
    }
    val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)
    message.setAttribute(TotalThrottleTimeAttribute(5000L))
    val taskResult = TaskResult(RUNNING)

    beforeGroup {
      whenever(task.execute(any<Stage<*>>())) doReturn taskResult
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
      whenever(task.timeout) doReturn timeout.toMillis()
    }

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("should not timeout and push the message back on the queue") {
      verify(queue).push(message, Duration.ofMillis(0))
    }
  }
})
