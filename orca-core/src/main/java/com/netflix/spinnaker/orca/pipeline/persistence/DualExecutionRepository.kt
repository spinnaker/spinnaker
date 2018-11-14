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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import rx.Observable

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
@ConditionalOnExpression("\${executionRepository.dual.enabled:false}")
class DualExecutionRepository(
  @Value("\${executionRepository.dual.primaryClass}") private val primaryClass: String,
  @Value("\${executionRepository.dual.previousClass}") private val previousClass: String,
  allRepositories: List<ExecutionRepository>
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
      allRepositories.find { repo ->
        repositoryClass.isInstance(repo) ||
          (repo is DelegatingExecutionRepository<*> && repositoryClass.isInstance(repo.getDelegate()))
      } ?: throw IllegalStateException("No ExecutionRepository bean of class $className found")
    }

    primary = findExecutionRepositoryByClass(primaryClass)
    previous = findExecutionRepositoryByClass(previousClass)
  }

  private fun select(execution: Execution): ExecutionRepository {
    return select(execution.type, execution.id)
  }

  private fun select(type: Execution.ExecutionType?, id: String): ExecutionRepository {
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

  override fun store(execution: Execution) {
    select(execution).store(execution)
  }

  override fun storeStage(stage: Stage) {
    select(stage.execution).storeStage(stage)
  }

  override fun updateStageContext(stage: Stage) {
    select(stage.execution).updateStageContext(stage)
  }

  override fun removeStage(execution: Execution, stageId: String) {
    select(execution).removeStage(execution, stageId)
  }

  override fun addStage(stage: Stage) {
    select(stage.execution).addStage(stage)
  }

  override fun cancel(type: Execution.ExecutionType, id: String) {
    select(type, id).cancel(type, id)
  }

  override fun cancel(type: Execution.ExecutionType, id: String, user: String?, reason: String?) {
    select(type, id).cancel(type, id, user, reason)
  }

  override fun pause(type: Execution.ExecutionType, id: String, user: String?) {
    select(type, id).pause(type, id, user)
  }

  override fun resume(type: Execution.ExecutionType, id: String, user: String?) {
    select(type, id).resume(type, id, user)
  }

  override fun resume(type: Execution.ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    select(type, id).resume(type, id, user, ignoreCurrentStatus)
  }

  override fun isCanceled(type: Execution.ExecutionType?, id: String): Boolean {
    return select(type, id).isCanceled(type, id)
  }

  override fun updateStatus(type: Execution.ExecutionType?, id: String, status: ExecutionStatus) {
    select(type, id).updateStatus(type, id, status)
  }

  override fun retrieve(type: Execution.ExecutionType, id: String): Execution {
    return select(type, id).retrieve(type, id)
  }

  override fun delete(type: Execution.ExecutionType, id: String) {
    return select(type, id).delete(type, id)
  }

  override fun retrieve(type: Execution.ExecutionType): Observable<Execution> {
    return Observable.merge(
      primary.retrieve(type),
      previous.retrieve(type)
    )
  }

  override fun retrieve(type: Execution.ExecutionType, criteria: ExecutionCriteria): Observable<Execution> {
    return Observable.merge(
      primary.retrieve(type, criteria),
      previous.retrieve(type, criteria)
    )
  }

  override fun retrievePipelinesForApplication(application: String): Observable<Execution> {
    return Observable.merge(
      primary.retrievePipelinesForApplication(application),
      previous.retrievePipelinesForApplication(application)
    )
  }

  override fun retrievePipelinesForPipelineConfigId(pipelineConfigId: String,
                                                    criteria: ExecutionCriteria): Observable<Execution> {
    return Observable.merge(
      primary.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria),
      previous.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
    )
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(pipelineConfigIds: MutableList<String>,
                                                                             buildTimeStartBoundary: Long,
                                                                             buildTimeEndBoundary: Long,
                                                                             limit: Int): Observable<Execution> {
    return Observable.merge(
      primary.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        limit
      ),
      previous.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        limit
      )
    )
  }

  override fun retrieveOrchestrationsForApplication(application: String,
                                                    criteria: ExecutionCriteria): Observable<Execution> {
    return Observable.merge(
      primary.retrieveOrchestrationsForApplication(application, criteria),
      previous.retrieveOrchestrationsForApplication(application, criteria)
    )
  }

  override fun retrieveOrchestrationsForApplication(application: String,
                                                    criteria: ExecutionCriteria,
                                                    sorter: ExecutionComparator?): MutableList<Execution> {
    val result = Observable.merge(
      Observable.from(primary.retrieveOrchestrationsForApplication(application, criteria, sorter)),
      Observable.from(previous.retrieveOrchestrationsForApplication(application, criteria, sorter))
    ).toList().toBlocking().single().toMutableList()

    return if (sorter != null) {
      result.asSequence().sortedWith(sorter as Comparator<in Execution>).toMutableList()
    } else {
      result
    }
  }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): Execution {
    return try {
      primary.retrieveOrchestrationForCorrelationId(correlationId)
    } catch (e: ExecutionNotFoundException) {
      previous.retrieveOrchestrationForCorrelationId(correlationId)
    }
  }

  override fun retrieveBufferedExecutions(): MutableList<Execution> {
    return Observable.merge(
      Observable.from(primary.retrieveBufferedExecutions()),
      Observable.from(previous.retrieveBufferedExecutions())
    ).toList().toBlocking().single()
  }

  override fun retrieveAllApplicationNames(executionType: Execution.ExecutionType?): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllApplicationNames(executionType)),
      Observable.from(previous.retrieveAllApplicationNames(executionType))
    ).toList().toBlocking().single().distinct().toMutableList()
  }

  override fun retrieveAllApplicationNames(executionType: Execution.ExecutionType?,
                                           minExecutions: Int): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllApplicationNames(executionType, minExecutions)),
      Observable.from(previous.retrieveAllApplicationNames(executionType, minExecutions))
    ).toList().toBlocking().single().distinct().toMutableList()
  }

  override fun hasExecution(type: Execution.ExecutionType, id: String): Boolean {
    return primary.hasExecution(type, id) || previous.hasExecution(type, id)
  }

  override fun retrieveAllExecutionIds(type: Execution.ExecutionType): MutableList<String> {
    return Observable.merge(
      Observable.from(primary.retrieveAllExecutionIds(type)),
      Observable.from(previous.retrieveAllExecutionIds(type))
    ).toList().toBlocking().single().distinct().toMutableList()
  }
}
