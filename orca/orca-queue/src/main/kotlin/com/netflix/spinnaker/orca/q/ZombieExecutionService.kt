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
package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.security.AuthenticatedRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Optional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Logic related to operating zombie pipeline executions.
 *
 * A zombie pipeline execution is a pipeline execution that:
 *
 * 1. Has a status of [ExecutionStatus.RUNNING].
 * 2. Does not have a message in the work queue.
 *
 * Until an overhaul on persistence is done, zombies occur intermittently even without major
 * outages, and are irrecoverable.
 */
@Component
@ConditionalOnBean(MonitorableQueue::class)
class ZombieExecutionService(
  private val executionRepository: ExecutionRepository,
  private val queue: MonitorableQueue,
  private val clock: Clock,
  @Qualifier("scheduler") private val scheduler: Optional<Scheduler>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Find all zombie pipeline executions.
   *
   * @param minimumInactivity The minimum amount of time an execution should be inactive before
   * being considered a candidate as a zombie. The shorter this timeframe is, the more likely
   * you will have half positives.
   */
  fun findAllZombies(minimumInactivity: Duration): List<PipelineExecution> {
    val criteria = ExecutionRepository.ExecutionCriteria().setStatuses(ExecutionStatus.RUNNING)
    return executionRepository.retrieve(PIPELINE, criteria)
      .mergeWith(executionRepository.retrieve(ORCHESTRATION, criteria))
      .subscribeOn(scheduler.orElseGet(Schedulers::io))
      .filter { hasBeenAroundAWhile(it, minimumInactivity) }
      .filter(this::queueHasNoMessages)
      .toList()
      .blockingGet()
  }

  /**
   * Find and kill all zombies.
   *
   * Since this pipeline can be highly disruptive to users in falsely identified zombies, the
   * default [minimumActivity] value of 60 minutes is the recommended low minimum setting. There
   * is no risk in letting a zombie "run", so be safe.
   */
  fun killZombies(minimumActivity: Duration = Duration.ofMinutes(60)) {
    findAllZombies(minimumActivity).forEach {
      killZombie(it)
    }
  }

  /**
   * Find and kill all zombies for an application.  Find zombies based on the specified
   * [minimumActivity] time.
   */
  fun killZombies(application: String, minimumActivity: Duration = Duration.ofMinutes(60)) {
    findAllZombies(minimumActivity)
      .filter { it.application ==  application }
      .forEach { zombie ->
        killZombie(zombie)
      }
  }

  /**
   * Kill a single zombie pipeline execution.
   *
   * WARNING: This method is unprotected: It will set a pipeline to canceled without verifying that it is not a zombie.
   * It is recommended to use [killZombies] instead.
   */
  fun killZombie(execution: PipelineExecution) {
    log.warn("Force cancelling zombie execution and all of its stages: ${execution.application}/${execution.id}")
    execution.stages
      .filter { it.status == ExecutionStatus.RUNNING }
      .forEach {
        it.status = ExecutionStatus.CANCELED
        it.endTime = clock.millis()
      }

    execution.status = ExecutionStatus.CANCELED
    execution.cancellationReason = "Identified as a zombie execution"
    execution.canceledBy = AuthenticatedRequest.getSpinnakerUser().orElse("admin")
  }

  private fun hasBeenAroundAWhile(execution: PipelineExecution, cutoff: Duration): Boolean =
    Instant.ofEpochMilli(execution.buildTime!!)
      .isBefore(clock.instant().minus(cutoff))

  private fun queueHasNoMessages(execution: PipelineExecution): Boolean =
    !queue.containsMessage { message ->
      message is ExecutionLevel && message.executionId == execution.id
    }
}
