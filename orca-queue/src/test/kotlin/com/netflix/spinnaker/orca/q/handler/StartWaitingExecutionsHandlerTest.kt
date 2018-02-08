/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import rx.Observable
import rx.Observable.empty
import rx.Observable.just
import java.time.Instant.now
import java.util.*
import kotlin.math.absoluteValue

object StartWaitingExecutionsHandlerTest : SubjectSpek<StartWaitingExecutionsHandler>({

  val queue = mock<Queue>()
  val repository = mock<ExecutionRepository>()

  fun resetMocks() {
    reset(queue, repository)
  }

  subject(GROUP) {
    StartWaitingExecutionsHandler(queue, repository)
  }

  val configId = UUID.randomUUID().toString()

  describe("starting waiting pipelines") {
    given("there are no pipelines waiting") {
      beforeGroup {
        whenever(
          repository.retrievePipelinesForPipelineConfigId(
            configId,
            ExecutionCriteria().setStatuses(NOT_STARTED).setLimit(Int.MAX_VALUE)
          )
        ) doReturn empty<Execution>()
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(StartWaitingExecutions(configId))
      }

      it("does nothing") {
        verifyZeroInteractions(queue)
      }
    }

    given("there is a single pipeline waiting") {
      val waitingPipeline = pipeline {
        pipelineConfigId = configId
      }

      beforeGroup {
        whenever(
          repository.retrievePipelinesForPipelineConfigId(
            configId,
            ExecutionCriteria().setStatuses(NOT_STARTED).setLimit(Int.MAX_VALUE)
          )
        ) doReturn just(waitingPipeline)
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(StartWaitingExecutions(configId))
      }

      it("starts the waiting pipeline") {
        verify(queue).push(StartExecution(waitingPipeline))
      }
    }

    given("multiple waiting pipelines") {
      val waitingPipelines = Random().let { rnd ->
        (1..5).map {
          pipeline {
            pipelineConfigId = configId
            buildTime = now().minusSeconds(rnd.nextInt().toLong().absoluteValue).toEpochMilli()
          }
        }
      }
      val oldest = waitingPipelines.minBy { it.buildTime!! }!!
      val newest = waitingPipelines.maxBy { it.buildTime!! }!!

      and("the queue should not be purged") {
        beforeGroup {
          whenever(
            repository.retrievePipelinesForPipelineConfigId(
              configId,
              ExecutionCriteria().setStatuses(NOT_STARTED).setLimit(Int.MAX_VALUE)
            )
          ) doReturn Observable.from(waitingPipelines)
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(StartWaitingExecutions(configId))
        }

        it("starts the oldest waiting pipeline") {
          verify(queue).push(StartExecution(oldest))
        }

        it("does nothing else") {
          verifyNoMoreInteractions(queue)
        }
      }

      and("the queue should be purged") {
        beforeGroup {
          whenever(
            repository.retrievePipelinesForPipelineConfigId(
              configId,
              ExecutionCriteria().setStatuses(NOT_STARTED).setLimit(Int.MAX_VALUE)
            )
          ) doReturn Observable.from(waitingPipelines)
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(StartWaitingExecutions(configId, purgeQueue = true))
        }

        it("starts the newest waiting pipeline") {
          verify(queue).push(StartExecution(newest))
        }

        it("cancels all the other waiting pipelines") {
          (waitingPipelines - newest).forEach {
            verify(queue).push(CancelExecution(it))
          }
        }

        it("does not cancel the one it's trying to start") {
          verify(queue, never()).push(CancelExecution(newest))
        }
      }
    }
  }
})
