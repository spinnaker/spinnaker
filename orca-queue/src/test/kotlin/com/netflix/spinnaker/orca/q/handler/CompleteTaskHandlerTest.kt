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
import com.netflix.spinnaker.orca.events.TaskComplete
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object CompleteTaskHandlerTest : SubjectSpek<CompleteTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  subject(GROUP) {
    CompleteTaskHandler(queue, repository, ContextParameterProcessor(), publisher, clock)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("when a task completes successfully") {
    given("the stage contains further tasks") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = multiTaskStage.type
          multiTaskStage.buildTasks(this)
        }
      }
      val message = CompleteTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", SUCCEEDED)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the task state in the stage") {
        verify(repository).storeStage(check {
          it.tasks.first().apply {
            assertThat(status).isEqualTo(SUCCEEDED)
            assertThat(endTime).isEqualTo(clock.millis())
          }
        })
      }

      it("runs the next task") {
        verify(queue)
          .push(StartTask(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId,
            "2"
          ))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<TaskComplete> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
          assertThat(it.taskId).isEqualTo(message.taskId)
          assertThat(it.status).isEqualTo(SUCCEEDED)
        })
      }
    }

    given("the stage is complete") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = singleTaskStage.type
          singleTaskStage.buildTasks(this)
        }
      }
      val message = CompleteTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", SUCCEEDED)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the task state in the stage") {
        verify(repository).storeStage(check {
          it.tasks.last().apply {
            assertThat(status).isEqualTo(SUCCEEDED)
            assertThat(endTime).isEqualTo(clock.millis())
          }
        })
      }

      it("emits an event to signal the stage is complete") {
        verify(queue)
          .push(CompleteStage(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId
          ))
      }
    }

    given("the stage has synthetic after stages") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = stageWithSyntheticAfter.type
          stageWithSyntheticAfter.buildTasks(this)
          stageWithSyntheticAfter.buildSyntheticStages(this)
        }
      }
      val message = CompleteTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", SUCCEEDED)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the task state in the stage") {
        verify(repository).storeStage(check {
          it.tasks.last().apply {
            assertThat(status).isEqualTo(SUCCEEDED)
            assertThat(endTime).isEqualTo(clock.millis())
          }
        })
      }

      it("triggers the first after stage") {
        verify(queue)
          .push(StartStage(
            message.executionType,
            message.executionId,
            "foo",
            pipeline.stages[1].id
          ))
      }
    }

    given("the task is the end of a rolling push loop") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = rollingPushStage.type
          rollingPushStage.buildTasks(this)
        }
      }

      and("when the task returns REDIRECT") {
        val message = CompleteTask(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "4", REDIRECT)

        beforeGroup {
          pipeline.stageByRef("1").apply {
            tasks[0].status = SUCCEEDED
            tasks[1].status = SUCCEEDED
            tasks[2].status = SUCCEEDED
          }

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("repeats the loop") {
          verify(queue).push(check<StartTask> {
            assertThat(it.taskId).isEqualTo("2")
          })
        }

        it("resets the status of the loop tasks") {
          verify(repository).storeStage(check {
            assertThat(it.tasks[1..3].map(Task::getStatus)).allMatch { it == NOT_STARTED }
          })
        }

        it("does not publish an event") {
          verifyZeroInteractions(publisher)
        }
      }
    }

  }

  setOf(TERMINAL, CANCELED).forEach { status ->
    describe("when a task completes with $status status") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = multiTaskStage.type
          multiTaskStage.buildTasks(this)
        }
      }
      val message = CompleteTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1", status)

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("updates the task state in the stage") {
        verify(repository).storeStage(check {
          it.tasks.first().apply {
            assertThat(status).isEqualTo(status)
            assertThat(endTime).isEqualTo(clock.millis())
          }
        })
      }

      it("fails the stage") {
        verify(queue).push(CompleteStage(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId
        ))
      }

      it("does not run the next task") {
        verify(queue, never()).push(any<RunTask>())
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<TaskComplete> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
          assertThat(it.taskId).isEqualTo(message.taskId)
          assertThat(it.status).isEqualTo(status)
        })
      }
    }
  }
})
