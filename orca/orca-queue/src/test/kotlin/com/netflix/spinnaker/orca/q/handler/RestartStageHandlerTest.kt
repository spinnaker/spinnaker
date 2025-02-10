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

import com.netflix.spinnaker.orca.DefaultStageResolver
import com.netflix.spinnaker.orca.NoOpTaskImplementationResolver
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StageDefinitionBuildersProvider
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.buildAfterStages
import com.netflix.spinnaker.orca.q.buildBeforeStages
import com.netflix.spinnaker.orca.q.buildTasks
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.orca.q.stageWithNestedSynthetics
import com.netflix.spinnaker.orca.q.stageWithSyntheticBefore
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.atLeast
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES
import kotlin.collections.contains
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.collections.set
import kotlin.collections.setOf
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.test.web.client.ExpectedCount.once
import io.reactivex.rxjava3.core.Observable

object RestartStageHandlerTest : SubjectSpek<RestartStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val pendingExecutionService: PendingExecutionService = mock()
  val clock = fixedClock()

  subject(GROUP) {
    RestartStageHandler(
      queue,
      repository,
      DefaultStageDefinitionBuilderFactory(
        DefaultStageResolver(
          StageDefinitionBuildersProvider(
            listOf(
              singleTaskStage,
              stageWithSyntheticBefore,
              stageWithNestedSynthetics
            )
          )
        )
      ),
      pendingExecutionService,
      clock
    )
  }

  fun resetMocks() = reset(queue, repository)

  ExecutionStatus
    .values()
    .filter { !it.isComplete && it != NOT_STARTED }
    .forEach { incompleteStatus ->
      describe("trying to restart a $incompleteStatus stage") {
        val pipeline = pipeline {
          application = "foo"
          status = RUNNING
          startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          stage {
            refId = "1"
            singleTaskStage.plan(this)
            status = incompleteStatus
            startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          }
        }
        val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "fzlem@netflix.com")

        beforeGroup {
          whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          subject.handle(message)
        }

        it("does not modify the stage status") {
          verify(repository, never()).store(any())
        }

        it("does not run the stage") {
          verify(queue, never()).push(any<StartStage>())
        }

        // TODO: should probably queue some kind of error
      }
    }

  setOf(TERMINAL, SUCCEEDED).forEach { stageStatus ->
    describe("restarting a $stageStatus stage") {
      val pipeline = pipeline {
        application = "foo"
        status = stageStatus
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
        stage {
          refId = "1"
          stageWithSyntheticBefore.plan(this)
          status = SUCCEEDED
          startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          stageWithNestedSynthetics.plan(this)
          stageWithNestedSynthetics.buildAfterStages(this)
          status = stageStatus
          startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
          endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
          context["exception"] = "o noes"
        }
      }
      val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("2").id, "fzlem@netflix.com")

      beforeGroup {
        stageWithSyntheticBefore.plan(pipeline.stageByRef("2>1"))

        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("resets the stage's status") {
        verify(repository).storeStage(
          check {
            assertThat(it.id).isEqualTo(message.stageId)
            assertThat(it.status).isEqualTo(NOT_STARTED)
            assertThat(it.startTime).isNull()
            assertThat(it.endTime).isNull()
          }
        )
      }

      it("removes the stage's tasks") {
        verify(repository).storeStage(
          check {
            assertThat(it.tasks).isEmpty()
          }
        )
      }

      it("adds restart details to the stage context") {
        verify(repository).storeStage(
          check {
            assertThat(it.context.keys).doesNotContain("exception")
            assertThat(it.context["restartDetails"]).isEqualTo(
              mapOf(
                "restartedBy" to "fzlem@netflix.com",
                "restartTime" to clock.millis(),
                "previousException" to "o noes"
              )
            )
          }
        )
      }

      it("removes the stage's synthetic stages") {
        pipeline
          .stages
          .filter { it.parentStageId == message.stageId }
          .map(StageExecution::getId)
          .forEach {
            verify(repository).removeStage(pipeline, it)
          }
      }

      val nestedSyntheticStageIds = pipeline
        .stages
        .filter { it.parentStageId == message.stageId }
        .map(StageExecution::getId)

      it("removes the nested synthetic stages") {
        assertThat(nestedSyntheticStageIds).isNotEmpty
        pipeline
          .stages
          .filter { it.parentStageId in nestedSyntheticStageIds }
          .map(StageExecution::getId)
          .forEach {
            verify(repository).removeStage(pipeline, it)
          }
      }

      it("does not affect preceding stages' synthetic stages") {
        setOf(pipeline.stageByRef("1").id)
          .flatMap { stageId -> pipeline.stages.filter { it.parentStageId == stageId } }
          .map { it.id }
          .forEach {
            verify(repository, never()).removeStage(any(), eq(it))
          }
      }

      it("marks the execution as running") {
        assertThat(pipeline.status).isEqualTo(RUNNING)
        verify(repository).updateStatus(pipeline)
      }

      it("runs the stage") {
        verify(queue).push(
          check<StartStage> {
            assertThat(it.executionType).isEqualTo(message.executionType)
            assertThat(it.executionId).isEqualTo(message.executionId)
            assertThat(it.application).isEqualTo(message.application)
            assertThat(it.stageId).isEqualTo(message.stageId)
          }
        )
      }
    }
  }

  describe("restarting a SUCCEEDED stage with downstream stages") {
    val pipeline = pipeline {
      application = "foo"
      status = SUCCEEDED
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("2")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(58, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(57, MINUTES).toEpochMilli()
      }
    }
    val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "fzlem@netflix.com")

    beforeGroup {
      whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("removes downstream stages' tasks") {
      val downstreamStageIds = setOf("2", "3").map { pipeline.stageByRef(it).id }
      argumentCaptor<StageExecutionImpl>().apply {
        verify(repository, atLeast(2)).storeStage(capture())
        downstreamStageIds.forEach {
          assertThat(allValues.map { it.id }).contains(it)
        }
        allValues.forEach {
          assertThat(it.tasks).isEmpty()
        }
      }
    }

    it("removes downstream stages' synthetic stages") {
      setOf("2", "3")
        .map { pipeline.stageByRef(it).id }
        .flatMap { stageId -> pipeline.stages.filter { it.parentStageId == stageId } }
        .map { it.id }
        .forEach {
          verify(repository).removeStage(pipeline, it)
        }
    }
  }

  describe("restarting a SUCCEEDED stage with a downstream join") {
    val pipeline = pipeline {
      application = "foo"
      status = SUCCEEDED
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("1", "2")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "4"
        requisiteStageRefIds = listOf("3")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(58, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(57, MINUTES).toEpochMilli()
      }
    }
    val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "fzlem@netflix.com")

    beforeGroup {
      whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("removes join stages' tasks") {
      val downstreamStageIds = setOf("1", "3", "4").map { pipeline.stageByRef(it).id }
      argumentCaptor<StageExecutionImpl>().apply {
        verify(repository, times(3)).storeStage(capture())
        assertThat(allValues.map { it.id }).isEqualTo(downstreamStageIds)
        allValues.forEach {
          assertThat(it.tasks).isEmpty()
        }
      }
    }

    it("removes join stages' synthetic stages") {
      setOf("3", "4")
        .map { pipeline.stageByRef(it).id }
        .flatMap { stageId -> pipeline.stages.filter { it.parentStageId == stageId } }
        .map { it.id }
        .forEach {
          verify(repository).removeStage(pipeline, it)
        }
    }
  }

  describe("restarting a synthetic stage restarts its parent") {
    val pipeline = pipeline {
      application = "foo"
      status = SUCCEEDED
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
    }

    val syntheticStage = pipeline.stages.first { it.parentStageId == pipeline.stageByRef("1").id }
    val message = RestartStage(pipeline.type, pipeline.id, "foo", syntheticStage.id, "fzlem@netflix.com")

    beforeGroup {
      whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("removes the original synthetic stage") {
      verify(repository).removeStage(pipeline, syntheticStage.id)
    }

    it("runs the parent stage") {
      verify(queue).push(
        check<StartStage> {
          assertThat(it.stageId).isEqualTo(pipeline.stageByRef("1").id)
          assertThat(it.stageId).isNotEqualTo(syntheticStage.id)
          assertThat(it.stageId).isNotEqualTo(message.stageId)
          assertThat(it.executionType).isEqualTo(message.executionType)
          assertThat(it.executionId).isEqualTo(message.executionId)
          assertThat(it.application).isEqualTo(message.application)
        }
      )
    }
  }

  describe("restarting a SUCCEEDED stage that should queue") {
    val pipeline = pipeline {
      pipelineConfigId = "bar"
      application = "foo"
      status = SUCCEEDED
      isLimitConcurrent = true
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
    }
    val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "fzlem@netflix.com")

    beforeGroup {
      val runningPipeline = pipeline {
        pipelineConfigId = pipeline.pipelineConfigId
        application = "foo"
        status = RUNNING
      }
      whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      whenever(repository.retrievePipelinesForPipelineConfigId(
        pipeline.pipelineConfigId,
        ExecutionRepository.ExecutionCriteria().setPageSize(2).setStatuses(RUNNING))) doReturn Observable.fromIterable(listOf(runningPipeline))
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("queues restart message") {
      verify(pendingExecutionService).enqueue(pipeline.pipelineConfigId, message)
      verify(queue, never()).push(any<StartStage>())
    }

    it("updates the pieline status to NOT_STARTED") {
      assertThat(pipeline.status).isEqualTo(NOT_STARTED)
      verify(repository).updateStatus(pipeline)
    }
  }

  describe("restarting a NOT_STARTED execution does not queue") {
    val pipeline = pipeline {
      pipelineConfigId = "bar"
      application = "foo"
      status = NOT_STARTED
      isLimitConcurrent = true
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = NOT_STARTED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
    }
    val message = RestartStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "fzlem@netflix.com")

    beforeGroup {
      val runningPipeline = pipeline {
        pipelineConfigId = pipeline.pipelineConfigId
        application = "foo"
        status = RUNNING
      }
      whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      whenever(repository.retrievePipelinesForPipelineConfigId(
        pipeline.pipelineConfigId,
        ExecutionRepository.ExecutionCriteria().setPageSize(2).setStatuses(RUNNING))) doReturn Observable.fromIterable(listOf(runningPipeline))
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("does not queue any messages") {
      verify(pendingExecutionService, never()).enqueue(pipeline.pipelineConfigId, message)
      verify(queue, never()).push(any<StartStage>())
    }
  }
})

fun StageDefinitionBuilder.plan(stage: StageExecution) {
  stage.type = type
  buildTasks(stage, NoOpTaskImplementationResolver())
  buildBeforeStages(stage)
}
