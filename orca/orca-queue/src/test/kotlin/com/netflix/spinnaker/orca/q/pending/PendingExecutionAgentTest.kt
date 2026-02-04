/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.q.pending

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import io.reactivex.rxjava3.core.Observable

internal object PendingExecutionAgentTest : SubjectSpek<PendingExecutionAgent>({
  val clusterLock: NotificationClusterLock = mock()
  val queue: Queue = mock()
  val pendingExecutionService: PendingExecutionService = mock()
  val executionRepository: ExecutionRepository = mock()

  class PendingExecutionAgentProxy : PendingExecutionAgent(
    clusterLock,
    NoopRegistry(),
    queue,
    pendingExecutionService,
    executionRepository,
    10000
  ) {
    public override fun tick() {
      super.tick()
    }
  }

  afterEachTest {
    reset(pendingExecutionService, executionRepository, queue)
  }

  describe("does nothing when there is nothing to do") {
    given("there are no pending executions") {
      beforeGroup {
        whenever(pendingExecutionService.pendingIds()) doReturn emptyList<String>()
      }
      on("agent tick") {
        PendingExecutionAgentProxy().tick()

        it("does nothing") {
          verify(queue, never()).push(any())
          verify(executionRepository, never()).retrievePipelinesForPipelineConfigId(any(), any())
        }
      }
    }
  }

  describe("does nothing when there is a running execution") {
    given("there is a pending execution but another of the same id is still running") {
      val runningPipeline = pipeline {
        pipelineConfigId = "ID1"
        status = ExecutionStatus.RUNNING
      }

      beforeGroup {
        whenever(pendingExecutionService.pendingIds()) doReturn listOf(runningPipeline.pipelineConfigId)
        whenever(
          executionRepository.retrievePipelinesForPipelineConfigId(
            runningPipeline.pipelineConfigId,
            ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.RUNNING)
          )
        ) doReturn Observable.just(runningPipeline)
      }

      on("agent tick") {
        PendingExecutionAgentProxy().tick()

        it("does nothing") {
          verify(queue, never()).push(any())
        }
      }
    }
  }

  describe("pushes pending message when execution is pending") {
    val completedPipeline = pipeline {
      pipelineConfigId = "ID1"
      isKeepWaitingPipelines = false
      status = ExecutionStatus.SUCCEEDED
    }
    beforeGroup {
      whenever(pendingExecutionService.pendingIds()) doReturn listOf(completedPipeline.pipelineConfigId)
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          completedPipeline.pipelineConfigId,
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.RUNNING)
        )
      ) doReturn Observable.empty<PipelineExecution>()
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          completedPipeline.pipelineConfigId,
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.COMPLETED.map { it.toString() })
        )
      ) doReturn Observable.just(completedPipeline)
    }

    on("agent tick") {
      PendingExecutionAgentProxy().tick()

      it("pushes a pending message with purging") {
        verify(queue, times(1)).push(StartWaitingExecutions(completedPipeline.pipelineConfigId, true))
      }
    }
  }

  describe("pushes pending message without purging when execution is pending") {
    val completedPipeline = pipeline {
      pipelineConfigId = "ID1"
      isKeepWaitingPipelines = true
      status = ExecutionStatus.SUCCEEDED
    }
    beforeGroup {
      whenever(pendingExecutionService.pendingIds()) doReturn listOf(completedPipeline.pipelineConfigId)
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          completedPipeline.pipelineConfigId,
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.RUNNING)
        )
      ) doReturn Observable.empty<PipelineExecution>()
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          completedPipeline.pipelineConfigId,
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.COMPLETED.map { it.toString() })
        )
      ) doReturn Observable.just(completedPipeline)
    }

    on("agent tick") {
      PendingExecutionAgentProxy().tick()

      it("pushes a pending message with purging") {
        verify(queue, times(1)).push(StartWaitingExecutions(completedPipeline.pipelineConfigId, false))
      }
    }
  }

  describe("pushes pending message when execution is pending and no prior executions") {
    beforeGroup {
      whenever(pendingExecutionService.pendingIds()) doReturn listOf("ID1")
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          "ID1",
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.RUNNING)
        )
      ) doReturn Observable.empty<PipelineExecution>()
      whenever(
        executionRepository.retrievePipelinesForPipelineConfigId(
          "ID1",
          ExecutionRepository.ExecutionCriteria().setPageSize(1).setStatuses(ExecutionStatus.COMPLETED.map { it.toString() })
        )
      ) doReturn Observable.empty<PipelineExecution>()
    }

    on("agent tick") {
      PendingExecutionAgentProxy().tick()

      it("pushes a pending message with purging") {
        verify(queue, times(1)).push(StartWaitingExecutions("ID1", false))
      }
    }
  }
})
