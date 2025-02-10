/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q.admin

import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.api.test.task
import com.netflix.spinnaker.orca.ext.beforeStages
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.DummyTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.orca.q.TasksProvider
import com.netflix.spinnaker.orca.q.handler.plan
import com.netflix.spinnaker.orca.q.stageWithSyntheticAfter
import com.netflix.spinnaker.orca.q.stageWithSyntheticBefore
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import io.reactivex.rxjava3.core.Observable

object HydrateQueueCommandTest : SubjectSpek<HydrateQueueCommand>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val taskResolver = TaskResolver(TasksProvider(emptyList()))

  subject(CachingMode.GROUP) {
    HydrateQueueCommand(queue, repository, taskResolver)
  }

  fun resetMocks() = reset(queue, repository)

  describe("hydrating a queue with no valid executions") {
    given("no running executions") {
      beforeGroup {
        whenever(repository.retrieve(any(), any<ExecutionCriteria>())) doReturn Observable.empty()
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("does nothing") {
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("running executions outside time window filter") {
      val oldPipeline = pipeline {
        status = RUNNING
        application = "whatever"
        startTime = 1500001000
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }
      val newPipeline = pipeline {
        status = RUNNING
        application = "whatever"
        startTime = 1500005000
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(oldPipeline, newPipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(
          HydrateQueueInput(
            start = Instant.ofEpochMilli(1500002000),
            end = Instant.ofEpochMilli(1500004000),
            dryRun = false
          )
        )

        it("does nothing") {
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("running executions not matching execution id filter") {
      val pipeline = pipeline {
        id = "1"
        status = NOT_STARTED
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(executionId = "2", dryRun = false))

        it("does nothing") {
          verifyNoMoreInteractions(queue)
        }
      }
    }
  }

  describe("hydrating a queue with running executions") {
    given("no running stages") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = NOT_STARTED
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          verify(queue, times(1)).push(
            check<StartStage> {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1").id)
            }
          )
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("all stages are complete") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = SUCCEEDED
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          verify(queue, times(1)).push(isA<CompleteExecution>())
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("synthetic before stages") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = stageWithSyntheticBefore.type
          status = RUNNING
          stageWithSyntheticBefore.plan(this)

          beforeStages().first().run {
            status = RUNNING
            task {
              id = "t1"
              implementingClass = DummyTask::class.java.name
              status = RUNNING
            }
          }
        }
        stage {
          refId = "2"
          type = stageWithSyntheticBefore.type
          status = NOT_STARTED
          stageWithSyntheticBefore.plan(this)
        }
        stage {
          refId = "3"
          type = "whatever"
          status = NOT_STARTED
          requisiteStageRefIds = listOf("1")
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          argumentCaptor<Message>().let {
            verify(queue, times(2)).push(it.capture())
            assertType<RunTask>(it.firstValue) {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1<1").id)
              assertThat(it.taskId).isEqualTo(pipeline.stageByRef("1<1").tasks[0].id)
            }
            assertType<StartStage>(it.secondValue) {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("2").id)
            }
          }
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("synthetic after stages") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = stageWithSyntheticAfter.type
          status = RUNNING
          stageWithSyntheticAfter.plan(this)
        }
      }
      pipeline.stageByRef("1").tasks.first().status = SUCCEEDED

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          argumentCaptor<CompleteStage>().let {
            verify(queue, times(1)).push(it.capture())
            assertThat(it.firstValue.stageId).isEqualTo(pipeline.stageByRef("1").id)
          }
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("stage task running") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = "stage1"
          status = RUNNING

          task {
            id = "t1"
            implementingClass = DummyTask::class.java.name
            status = SUCCEEDED
          }
          task {
            id = "t2"
            implementingClass = DummyTask::class.java.name
            status = RUNNING
          }
        }
        stage {
          refId = "2"
          type = "stage2"
          status = NOT_STARTED

          task {
            id = "t1"
            implementingClass = DummyTask::class.java.name
            status = NOT_STARTED
          }
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          argumentCaptor<Message>().let {
            verify(queue, times(2)).push(it.capture())

            assertType<RunTask>(it.firstValue) {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1").id)
              assertThat(it.taskId).isEqualTo(pipeline.stageByRef("1").taskById("t2").id)
            }
            assertType<StartStage>(it.secondValue) {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("2").id)
            }
          }
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("stage task not started") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = ""
          status = RUNNING

          task {
            id = "t1"
            status = NOT_STARTED
          }
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          verify(queue, times(1)).push(
            check<StartTask> {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1").id)
              assertThat(it.taskId).isEqualTo(pipeline.stageByRef("1").taskById("t1").id)
            }
          )
          verifyNoMoreInteractions(queue)
        }
      }
    }

    given("stage tasks complete") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING

          task {
            id = "t1"
            status = SUCCEEDED
          }
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        subject.invoke(HydrateQueueInput(dryRun = false))

        it("adds messages to the queue") {
          verify(queue, times(1)).push(
            check<CompleteStage> {
              assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1").id)
            }
          )
          verifyNoMoreInteractions(queue)
        }
      }
    }
  }

  describe("dry running hydration") {
    given("running executions") {
      val pipeline = pipeline {
        status = RUNNING
        application = "whatever"
        stage {
          refId = "1"
          type = "whatever"
          status = NOT_STARTED
        }
      }

      beforeGroup {
        whenever(repository.retrieve(eq(ORCHESTRATION), any<ExecutionCriteria>())) doReturn Observable.empty()
        whenever(repository.retrieve(eq(PIPELINE), any<ExecutionCriteria>())) doReturn Observable.just(pipeline)
      }
      afterGroup(::resetMocks)

      on("invoking") {
        val output = subject.invoke(HydrateQueueInput(dryRun = true))

        it("does not interact with queue") {
          verifyNoMoreInteractions(queue)
        }

        it("emits dry run output") {
          assertThat(output.dryRun).isTrue()
          assertThat(output.executions).isNotEmpty
          assertThat(output.executions[pipeline.id]!!.actions).hasOnlyOneElementSatisfying {
            assertThat(it.message).isNotNull()
            assertThat(it.message).isInstanceOf(StartStage::class.java)
            assertThat(it.description).contains("Stage is not started")
          }
        }
      }
    }
  }
})

private inline fun <reified T> assertType(subject: Any, assertions: (T) -> Unit) {
  assertThat(subject).isInstanceOf(T::class.java)
  if (subject is T) {
    assertions(subject)
  }
}
