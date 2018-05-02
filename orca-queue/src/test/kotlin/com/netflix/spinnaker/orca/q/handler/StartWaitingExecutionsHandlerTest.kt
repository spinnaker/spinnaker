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

import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.orca.queueing.PipelineQueue
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Instant.now
import java.util.*
import kotlin.math.absoluteValue

object StartWaitingExecutionsHandlerTest : SubjectSpek<StartWaitingExecutionsHandler>({

  val queue = mock<Queue>()
  val repository = mock<ExecutionRepository>()
  val pipelineQueue = mock<PipelineQueue>()

  fun resetMocks() {
    reset(queue, repository)
  }

  subject(GROUP) {
    StartWaitingExecutionsHandler(queue, repository, pipelineQueue)
  }

  val configId = UUID.randomUUID().toString()

  describe("starting waiting pipelines") {
    given("there are no pipelines waiting") {
      beforeGroup {
        whenever(pipelineQueue.depth(configId)) doReturn 0
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

      given("the queue should not be purged") {
        beforeGroup {
          whenever(pipelineQueue.depth(configId)) doReturn 1
          whenever(pipelineQueue.popOldest(configId)) doReturn StartExecution(waitingPipeline)
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(StartWaitingExecutions(configId))
        }

        it("starts the waiting pipeline") {
          verify(queue).push(StartExecution(waitingPipeline))
        }
      }

      given("the queue should be purged") {
        beforeGroup {
          whenever(pipelineQueue.depth(configId)) doReturn 1
          whenever(pipelineQueue.popNewest(configId)) doReturn StartExecution(waitingPipeline)
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(StartWaitingExecutions(configId, purgeQueue = true))
        }

        it("starts the waiting pipeline") {
          verify(queue).push(StartExecution(waitingPipeline))
        }

        it("does not cancel anything") {
          verify(queue, never()).push(isA<CancelExecution>())
        }
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

      given("the queue should not be purged") {
        beforeGroup {
          whenever(pipelineQueue.depth(configId)) doReturn waitingPipelines.size
          whenever(pipelineQueue.popOldest(configId)) doReturn StartExecution(oldest)
          whenever(pipelineQueue.popNewest(configId)) doReturn StartExecution(newest)
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

      given("the queue should be purged") {
        beforeGroup {
          whenever(pipelineQueue.depth(configId)) doReturn waitingPipelines.size
          whenever(pipelineQueue.popOldest(configId)) doReturn StartExecution(oldest)
          whenever(pipelineQueue.popNewest(configId)) doReturn StartExecution(newest)
          argumentCaptor<(Message) -> Unit>().apply {
            whenever(pipelineQueue.purge(eq(configId), capture())).then {
              (waitingPipelines - newest).forEach {
                firstValue.invoke(StartExecution(it))
              }
            }
          }
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(StartWaitingExecutions(configId, purgeQueue = true))
        }

        it("starts the newest waiting pipeline") {
          verify(queue).push(StartExecution(newest))
        }

        it("cancels all the other waiting pipelines") {
          verify(queue, times(waitingPipelines.size - 1)).push(isA<CancelExecution>())
        }

        it("does not cancel the one it's trying to start") {
          verify(queue, never()).push(CancelExecution(newest))
        }
      }
    }
  }
})
