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
package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import rx.Observable
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nonnull

class InMemoryExecutionRepository : ExecutionRepository {

  private val correlationIds: MutableMap<String, String> = ConcurrentHashMap()
  private val pipelines: MutableMap<String, PipelineExecution> = ConcurrentHashMap()
  private val orchestrations: MutableMap<String, PipelineExecution> = ConcurrentHashMap()

  override fun updateStatus(type: ExecutionType, id: String, status: ExecutionStatus) {
    retrieve(type, id).status = status
  }

  override fun addStage(stage: StageExecution) {
    // Do nothing, in-memory this would actually already be done.
  }

  override fun retrieveAllApplicationNames(executionType: ExecutionType?): MutableList<String> {
    return if (executionType == null) {
      pipelines.values + orchestrations.values
    } else {
      storageFor(executionType).values
    }
      .map { it.application }
      .toMutableList()
  }

  override fun retrieveAllApplicationNames(executionType: ExecutionType?, minExecutions: Int): MutableList<String> {
    return if (executionType == null) {
      pipelines.values + orchestrations.values
    } else {
      storageFor(executionType).values
    }
      .groupBy { it.application }
      .filter { it.value.size >= minExecutions }
      .keys
      .toMutableList()
  }

  override fun cancel(type: ExecutionType, id: String) {
    cancel(type, id, null, null)
  }

  override fun cancel(type: ExecutionType, id: String, user: String?, reason: String?) {
    retrieve(type, id).also {
      it.status = ExecutionStatus.CANCELED
      if (user != null) {
        it.canceledBy = user
      }
      if (!reason.isNullOrEmpty()) {
        it.cancellationReason = reason
      }
      store(it)
    }
  }

  override fun resume(type: ExecutionType, id: String, user: String?) {
    resume(type, id, user, false)
  }

  override fun resume(type: ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    retrieve(type, id).also {
      if (!ignoreCurrentStatus && it.status != ExecutionStatus.PAUSED) {
        throw UnresumablePipelineException(
          "Unable to resume pipeline that is not PAUSED " +
            "(executionId: ${it.id}, currentStatus: ${it.status}"
        )
      }
      it.status = ExecutionStatus.RUNNING
      it.paused?.resumedBy = user
      it.paused?.resumeTime = currentTimeMillis()
      store(it)
    }
  }

  override fun isCanceled(type: ExecutionType, id: String): Boolean {
    return retrieve(type, id).isCanceled
  }

  override fun retrieveByCorrelationId(executionType: ExecutionType, correlationId: String): PipelineExecution {
    return storageFor(executionType)
      .let {
        if (!correlationIds.containsKey(correlationId)) {
          null
        } else {
          it[correlationIds[correlationId]]
        }
      }
      ?.also {
        if (it.status.isComplete) {
          correlationIds.remove(correlationId)
        }
      }
      ?: throw ExecutionNotFoundException("No $executionType found for correlation ID $correlationId")
  }

  override fun delete(type: ExecutionType, id: String) {
    storageFor(type).remove(id)
  }

  override fun delete(type: ExecutionType, idsToDelete: MutableList<String>) {
    val storage = storageFor(type)
    idsToDelete.forEach { id -> storage.remove(id) }
  }

  override fun hasExecution(type: ExecutionType, id: String): Boolean {
    return storageFor(type).containsKey(id)
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    retrieve(type, id).also {
      if (it.status != ExecutionStatus.RUNNING) {
        throw UnpausablePipelineException(
          "Unable to pause pipeline that is not RUNNING " +
            "(executionId: ${it.id}, currentStatus: ${it.status})"
        )
      }
      it.status = ExecutionStatus.PAUSED
      it.paused = PipelineExecution.PausedDetails().apply {
        pausedBy = user
        pauseTime = currentTimeMillis()
      }
      store(it)
    }
  }

  override fun retrieve(type: ExecutionType, id: String): PipelineExecution {
    return storageFor(type)[id] ?: throw ExecutionNotFoundException("No $type found for $id")
  }

  override fun retrieve(type: ExecutionType): Observable<PipelineExecution> {
    return Observable.from(storageFor(type).values)
  }

  override fun retrieve(type: ExecutionType, criteria: ExecutionCriteria): Observable<PipelineExecution> {
    return Observable.from(storageFor(type).values)
  }

  override fun store(execution: PipelineExecution) {
    storageFor(execution.type).run {
      if (!containsKey(execution.id)) {
        put(execution.id, execution)
      }
    }

    if (execution.buildTime == null) {
      execution.buildTime = currentTimeMillis()
    }
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    return storageFor(type).keys.toMutableList()
  }

  override fun updateStageContext(stage: StageExecution) {
    // Do nothing
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): PipelineExecution {
    return retrieveByCorrelationId(PIPELINE, correlationId)
  }

  override fun retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: MutableList<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): MutableList<PipelineExecution> {
    val all = mutableListOf<PipelineExecution>()
    var page = 1
    var pageSize = executionCriteria.pageSize
    var moreResults = true

    while (moreResults) {
      val results = retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds, buildTimeStartBoundary, buildTimeEndBoundary, executionCriteria.setPage(page)
      )
      moreResults = results.size >= pageSize
      page += 1

      all.addAll(results)
    }

    return all
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: MutableList<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): MutableList<PipelineExecution> {
    return pipelines.values
      .filter { pipelineConfigIds.contains(it.pipelineConfigId) }
      .filter { it.buildTime in (buildTimeStartBoundary + 1) until buildTimeEndBoundary }
      .applyCriteria(executionCriteria)
      .toMutableList()
  }

  override fun retrieveBufferedExecutions(): MutableList<PipelineExecution> {
    return (pipelines.values + orchestrations.values)
      .filter { it.status == ExecutionStatus.BUFFERED }
      .toMutableList()
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    return Observable.from(
      orchestrations.values
        .filter { it.application == application }
        .applyCriteria(criteria)
    )
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<PipelineExecution> {
    return orchestrations.values
      .filter { it.application == application }
      .applyCriteria(criteria)
      .sortedByExecutionComparator(sorter)
      .toMutableList()
  }

  override fun retrievePipelinesForApplication(application: String): Observable<PipelineExecution> {
    return Observable.from(
      pipelines.values
        .filter { it.application == application }
    )
  }

  override fun removeStage(execution: PipelineExecution, stageId: String) {
    execution.stages.removeIf { it.id == stageId }
    store(execution)
  }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    return Observable.from(
      pipelines.values
        .filter { it.pipelineConfigId == pipelineConfigId }
        .applyCriteria(criteria)
    )
  }

  override fun retrievePipelineConfigIdsForApplication(application: String): List<String> {
    return pipelines.values
      .filter { it.application == application }
      .map { it.pipelineConfigId }
      .distinct()
  }

  override fun retrieveAndFilterPipelineExecutionIdsForApplication(
    @Nonnull application: String,
    @Nonnull pipelineConfigIds: List<String>,
    @Nonnull criteria: ExecutionCriteria
  ): List<String> {
    return pipelines.values
      .filter { it.application == application && pipelineConfigIds.contains(it.pipelineConfigId) }
      .applyCriteria(criteria)
      .map { it.id }
  }

  override fun retrievePipelineExecutionDetailsForApplication(
    application: String,
    pipelineConfigIds: List<String>,
    queryTimeoutSeconds: Int
  ): Collection<PipelineExecution> {
    return pipelines.values
      .filter { it.application == application && pipelineConfigIds.contains(it.pipelineConfigId) }
      .distinctBy { it.id }
  }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): PipelineExecution {
    return retrieveByCorrelationId(ORCHESTRATION, correlationId)
  }

  override fun storeStage(stage: StageExecution) {
    // Do nothing
  }

  private fun storageFor(executionType: ExecutionType): MutableMap<String, PipelineExecution> =
    when (executionType) {
      PIPELINE -> pipelines
      ORCHESTRATION -> orchestrations
    }

  private fun storageFor(executionId: String): MutableMap<String, PipelineExecution>? =
    when {
      pipelines.containsKey(executionId) -> pipelines
      orchestrations.containsKey(executionId) -> orchestrations
      else -> null
    }

  private fun List<PipelineExecution>.applyCriteria(criteria: ExecutionCriteria): List<PipelineExecution> {
    return filter { criteria.statuses.contains(it.status) }
      .filter { Instant.ofEpochMilli(it.startTime).isAfter(criteria.startTimeCutoff) }
      .chunked(criteria.pageSize)[criteria.page]
  }

  private fun List<PipelineExecution>.sortedByExecutionComparator(
    comparator: ExecutionComparator?
  ): List<PipelineExecution> {
    return if (comparator == null) {
      sortedByDescending { it.id }
    } else {
      sortedWith(comparator)
    }
  }
}
