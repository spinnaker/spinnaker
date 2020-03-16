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
package com.netflix.spinnaker.orca.telemetry

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.persistence.DelegatingExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.RedisExecutionRepository
import rx.Observable
import java.util.concurrent.TimeUnit

class RedisInstrumentedExecutionRepository(
  private val executionRepository: RedisExecutionRepository,
  private val registry: Registry
) : DelegatingExecutionRepository<RedisExecutionRepository> {
  private fun invocationId(method: String) =
    registry.createId("redis.executionRepository.$method.invocations")

  private fun timingId(method: String) =
    registry.createId("redis.executionRepository.$method.timing")

  private fun <T> withMetrics(method: String, fn: () -> T): T {
    val start = System.currentTimeMillis()

    try {
      val result = fn()
      registry.counter(invocationId(method).withTag("result", "success")).increment()
      recordTiming(timingId(method).withTag("result", "success"), start)
      return result
    } catch (e: Exception) {
      registry.counter(invocationId(method).withTag("result", "failure")).increment()
      recordTiming(timingId(method).withTag("result", "failure"), start)
      throw e
    }
  }

  private fun recordTiming(id: Id, startTimeMs: Long) {
    PercentileTimer.get(registry, id).record(System.currentTimeMillis() - startTimeMs, TimeUnit.MILLISECONDS)
  }

  override fun getDelegate() = executionRepository

  override fun store(execution: PipelineExecution) {
    withMetrics("store") {
      executionRepository.store(execution)
    }
  }

  override fun storeStage(stage: StageExecution) {
    withMetrics("storeStage") {
      executionRepository.storeStage(stage)
    }
  }

  override fun updateStageContext(stage: StageExecution) {
    withMetrics("updateStageContext") {
      executionRepository.updateStageContext(stage)
    }
  }

  override fun removeStage(execution: PipelineExecution, stageId: String) {
    withMetrics("removeStage") {
      executionRepository.removeStage(execution, stageId)
    }
  }

  override fun addStage(stage: StageExecution) {
    withMetrics("addStage") {
      executionRepository.addStage(stage)
    }
  }

  override fun cancel(type: ExecutionType, id: String) {
    withMetrics("cancel2") {
      executionRepository.cancel(type, id)
    }
  }

  override fun cancel(type: ExecutionType, id: String, user: String?, reason: String?) {
    withMetrics("cancel4") {
      executionRepository.cancel(type, id, user, reason)
    }
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    withMetrics("pause") {
      executionRepository.pause(type, id, user)
    }
  }

  override fun resume(type: ExecutionType, id: String, user: String?) {
    withMetrics("resume3") {
      executionRepository.resume(type, id, user)
    }
  }

  override fun resume(type: ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    withMetrics("resume4") {
      executionRepository.resume(type, id, user, ignoreCurrentStatus)
    }
  }

  override fun isCanceled(type: ExecutionType, id: String): Boolean {
    return withMetrics("isCanceled") {
      executionRepository.isCanceled(type, id)
    }
  }

  override fun updateStatus(type: ExecutionType, id: String, status: ExecutionStatus) {
    withMetrics("updateStatus") {
      executionRepository.updateStatus(type, id, status)
    }
  }

  override fun delete(type: ExecutionType, id: String) {
    withMetrics("delete") {
      executionRepository.delete(type, id)
    }
  }

  override fun retrieve(type: ExecutionType, id: String): PipelineExecution {
    return withMetrics("retrieve2") {
      executionRepository.retrieve(type, id)
    }
  }

  override fun retrieve(type: ExecutionType): Observable<PipelineExecution> {
    return withMetrics("retrieve1") {
      executionRepository.retrieve(type)
    }
  }

  override fun retrieve(type: ExecutionType, criteria: ExecutionRepository.ExecutionCriteria): Observable<PipelineExecution> {
    return withMetrics("retrieve3") {
      executionRepository.retrieve(type, criteria)
    }
  }

  override fun retrieveBufferedExecutions(): MutableList<PipelineExecution> {
    return withMetrics("retrieveBufferedExecutions") {
      executionRepository.retrieveBufferedExecutions()
    }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionRepository.ExecutionCriteria
  ): Observable<PipelineExecution> {
    return withMetrics("retrieveOrchestrationsForApplication") {
      executionRepository.retrieveOrchestrationsForApplication(application, criteria)
    }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionRepository.ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<PipelineExecution> {
    return withMetrics("retrieveOrchestrationsForApplication3") {
      executionRepository.retrieveOrchestrationsForApplication(application, criteria, sorter)
    }
  }

  override fun retrievePipelinesForApplication(application: String): Observable<PipelineExecution> {
    return withMetrics("retrievePipelinesForApplication") {
      executionRepository.retrievePipelinesForApplication(application)
    }
  }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionRepository.ExecutionCriteria
  ): Observable<PipelineExecution> {
    return withMetrics("retrievePipelinesForPipelineConfigId") {
      executionRepository.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
    }
  }

  override fun retrieveByCorrelationId(executionType: ExecutionType, correlationId: String): PipelineExecution {
    return withMetrics("retrieveByCorrelationId") {
      executionRepository.retrieveByCorrelationId(executionType, correlationId)
    }
  }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): PipelineExecution {
    return withMetrics("retrieveOrchestrationForCorrelationId") {
      executionRepository.retrieveOrchestrationForCorrelationId(correlationId)
    }
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): PipelineExecution {
    return withMetrics("retrievePipelineForCorrelationId") {
      executionRepository.retrievePipelineForCorrelationId(correlationId)
    }
  }

  override fun retrieveAllApplicationNames(type: ExecutionType?): List<String> {
    return withMetrics("retrieveAllApplicationNames1") {
      executionRepository.retrieveAllApplicationNames(type)
    }
  }

  override fun retrieveAllApplicationNames(type: ExecutionType?, minExecutions: Int): List<String> {
    return withMetrics("retrieveAllApplicationNames2") {
      executionRepository.retrieveAllApplicationNames(type, minExecutions)
    }
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: MutableList<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionRepository.ExecutionCriteria
  ): List<PipelineExecution> {
    return withMetrics("retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary") {
      executionRepository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria)
    }
  }

  override fun retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: List<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionRepository.ExecutionCriteria
  ): List<PipelineExecution> {
    return withMetrics("retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary") {
      executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria)
    }
  }

  override fun hasExecution(type: ExecutionType, id: String): Boolean {
    return withMetrics("hasExecution") {
      executionRepository.hasExecution(type, id)
    }
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    return withMetrics("retrieveAllExecutionIds") {
      executionRepository.retrieveAllExecutionIds(type)
    }
  }
}
