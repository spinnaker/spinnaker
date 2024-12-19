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
package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import rx.Observable
import javax.annotation.Nonnull

/**
 * Intended for performing red/black Orca deployments which do not share the
 * same persistence backend.
 *
 * In order to use this class, you will need to enable it and then define the
 * class name of both primary and previous execution repositories. It's
 * expected that you have multiple execution repository backends wired up.
 */
@Primary
@Component
@ConditionalOnExpression("\${execution-repository.dual.enabled:false}")
class DualExecutionRepository(
  @Value("\${execution-repository.dual.primary-class:}") private val primaryClass: String,
  @Value("\${execution-repository.dual.previous-class:}") private val previousClass: String,
  @Value("\${execution-repository.dual.primary-name:}") private val primaryName: String,
  @Value("\${execution-repository.dual.previous-name:}") private val previousName: String,
  allRepositories: List<ExecutionRepository>,
  applicationContext: ApplicationContext
) : ExecutionRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  lateinit var primary: ExecutionRepository
  lateinit var previous: ExecutionRepository

  init {
    allRepositories.forEach {
      log.info("Available ExecutionRepository: $it")
    }

    val findExecutionRepositoryByClass = { className: String ->
      val repositoryClass = Class.forName(className)
      allRepositories
        .find { repositoryClass.isInstance(it) }
        ?: throw IllegalStateException("No ExecutionRepository bean of class $className found")
    }

    val findExecutionRepository = { beanName: String, beanClass: String ->
      if (beanName.isNotBlank()) {
        applicationContext.getBean(beanName) as ExecutionRepository
      } else {
        findExecutionRepositoryByClass(beanClass)
      }
    }

    primary = findExecutionRepository(primaryName, primaryClass)
    previous = findExecutionRepository(previousName, previousClass)
  }

  private fun select(execution: PipelineExecution): ExecutionRepository {
    return select(execution.type, execution.id)
  }

  private fun select(type: ExecutionType?, id: String): ExecutionRepository {
    if (type == null) {
      return select(id)
    } else {
      if (primary.hasExecution(type, id)) {
        return primary
      } else if (previous.hasExecution(type, id)) {
        return previous
      }
      return primary
    }
  }

  private fun select(id: String): ExecutionRepository {
    return when {
      primary.hasExecution(PIPELINE, id) -> primary
      previous.hasExecution(PIPELINE, id) -> previous
      primary.hasExecution(ORCHESTRATION, id) -> primary
      previous.hasExecution(ORCHESTRATION, id) -> previous
      else -> primary
    }
  }

  override fun store(execution: PipelineExecution) {
    select(execution).store(execution)
  }

  override fun storeStage(stage: StageExecution) {
    select(stage.execution).storeStage(stage)
  }

  override fun updateStageContext(stage: StageExecution) {
    select(stage.execution).updateStageContext(stage)
  }

  override fun removeStage(execution: PipelineExecution, stageId: String) {
    select(execution).removeStage(execution, stageId)
  }

  override fun addStage(stage: StageExecution) {
    select(stage.execution).addStage(stage)
  }

  override fun cancel(type: ExecutionType, id: String) {
    select(type, id).cancel(type, id)
  }

  override fun cancel(type: ExecutionType, id: String, user: String?, reason: String?) {
    select(type, id).cancel(type, id, user, reason)
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    select(type, id).pause(type, id, user)
  }

  override fun resume(type: ExecutionType, id: String, user: String?) {
    select(type, id).resume(type, id, user)
  }

  override fun resume(type: ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    select(type, id).resume(type, id, user, ignoreCurrentStatus)
  }

  override fun isCanceled(type: ExecutionType?, id: String): Boolean {
    return select(type, id).isCanceled(type, id)
  }

  override fun updateStatus(type: ExecutionType?, id: String, status: ExecutionStatus) {
    select(type, id).updateStatus(type, id, status)
  }

  override fun retrieve(type: ExecutionType, id: String): PipelineExecution {
    return select(type, id).retrieve(type, id)
  }

  override fun delete(type: ExecutionType, id: String) {
    return select(type, id).delete(type, id)
  }

  override fun delete(type: ExecutionType, idsToDelete: MutableList<String>) {
    // NOTE: Not a great implementation, but this method right now is only used on SqlExecutionRepository which has
    // a performant implementation
    idsToDelete.forEach { id ->
      delete(type, id)
    }
  }

  override fun retrieve(type: ExecutionType): Observable<PipelineExecution> {
    return Observable.merge(
      primary.retrieve(type),
      previous.retrieve(type)
    ).distinct { it.id }
  }

  override fun retrieve(type: ExecutionType, criteria: ExecutionCriteria): Observable<PipelineExecution> {
    return Observable.merge(
      primary.retrieve(type, criteria),
      previous.retrieve(type, criteria)
    ).distinct { it.id }
  }

  override fun retrievePipelinesForApplication(application: String): Observable<PipelineExecution> {
    return Observable.merge(
      primary.retrievePipelinesForApplication(application),
      previous.retrievePipelinesForApplication(application)
    ).distinct { it.id }
  }

  override fun retrievePipelineConfigIdsForApplication(application: String): List<String> {
    return (
      primary.retrievePipelineConfigIdsForApplication(application) +
      previous.retrievePipelineConfigIdsForApplication(application)
      ).distinct()
  }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    return Observable.merge(
      primary.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria),
      previous.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
    ).distinct { it.id }
  }

  override fun retrieveAndFilterPipelineExecutionIdsForApplication(
    @Nonnull application: String,
    @Nonnull pipelineConfigIds: List<String>,
    @Nonnull criteria: ExecutionCriteria
  ): List<String> {
    return primary.retrieveAndFilterPipelineExecutionIdsForApplication(application, pipelineConfigIds, criteria) +
      previous.retrieveAndFilterPipelineExecutionIdsForApplication(application, pipelineConfigIds, criteria)
  }

  override fun retrievePipelineExecutionDetailsForApplication(
    @Nonnull application: String,
    pipelineConfigIds: List<String>,
    queryTimeoutSeconds: Int
  ): Collection<PipelineExecution> {
    return (
      primary.retrievePipelineExecutionDetailsForApplication(application, pipelineConfigIds, queryTimeoutSeconds) +
      previous.retrievePipelineExecutionDetailsForApplication(application, pipelineConfigIds, queryTimeoutSeconds)
      ).distinctBy { it.id }
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: MutableList<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<PipelineExecution> {
    return primary
      .retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria
      )
      .plus(
        previous.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
          pipelineConfigIds,
          buildTimeStartBoundary,
          buildTimeEndBoundary,
          executionCriteria
        )
      ).distinctBy { it.id }
  }

  override fun retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: List<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<PipelineExecution> {
    return primary
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria
      ).plus(
        previous.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
          pipelineConfigIds,
          buildTimeStartBoundary,
          buildTimeEndBoundary,
          executionCriteria
        )
      ).distinctBy { it.id }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    return Observable.merge(
      primary.retrieveOrchestrationsForApplication(application, criteria),
      previous.retrieveOrchestrationsForApplication(application, criteria)
    ).distinct { it.id }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<PipelineExecution> {
    val result = Observable.merge(
      Observable.from(primary.retrieveOrchestrationsForApplication(application, criteria, sorter)),
      Observable.from(previous.retrieveOrchestrationsForApplication(application, criteria, sorter))
    ).toList().toBlocking().single().distinctBy { it.id }.toMutableList()

    return if (sorter != null) {
      result.asSequence().sortedWith(sorter as Comparator<in PipelineExecution>).toMutableList()
    } else {
      result
    }
  }

  override fun retrieveByCorrelationId(executionType: ExecutionType, correlationId: String): PipelineExecution {
    return try {
      primary.retrieveByCorrelationId(executionType, correlationId)
    } catch (e: ExecutionNotFoundException) {
      previous.retrieveByCorrelationId(executionType, correlationId)
    }
  }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): PipelineExecution {
    return try {
      primary.retrieveOrchestrationForCorrelationId(correlationId)
    } catch (e: ExecutionNotFoundException) {
      previous.retrieveOrchestrationForCorrelationId(correlationId)
    }
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): PipelineExecution {
    return try {
      primary.retrievePipelineForCorrelationId(correlationId)
    } catch (e: ExecutionNotFoundException) {
      previous.retrievePipelineForCorrelationId(correlationId)
    }
  }

  override fun retrieveBufferedExecutions(): MutableList<PipelineExecution> {
    return Observable.merge(
      Observable.from(primary.retrieveBufferedExecutions()),
      Observable.from(previous.retrieveBufferedExecutions())
    ).toList().toBlocking().single().distinctBy { it.id }.toMutableList()
  }

  override fun retrieveAllApplicationNames(executionType: ExecutionType?): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllApplicationNames(executionType)),
      Observable.from(previous.retrieveAllApplicationNames(executionType))
    ).toList().toBlocking().single().distinct().toMutableList()
  }

  override fun retrieveAllApplicationNames(
    executionType: ExecutionType?,
    minExecutions: Int
  ): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllApplicationNames(executionType, minExecutions)),
      Observable.from(previous.retrieveAllApplicationNames(executionType, minExecutions))
    ).toList().toBlocking().single().distinct().toMutableList()
  }

  override fun hasExecution(type: ExecutionType, id: String): Boolean {
    return primary.hasExecution(type, id) || previous.hasExecution(type, id)
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllExecutionIds(type)),
      Observable.from(previous.retrieveAllExecutionIds(type))
    ).toList().toBlocking().single().distinct().toMutableList()
  }
}
