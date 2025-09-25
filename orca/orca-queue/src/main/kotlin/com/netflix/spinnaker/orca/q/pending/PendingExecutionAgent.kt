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

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("\${pollers.pending-execution-service-agent.enabled:false}")
class PendingExecutionAgent(
  clusterLock: NotificationClusterLock,
  registry: Registry,
  private val queue: Queue,
  private val pendingExecutionService: PendingExecutionService,
  private val executionRepository: ExecutionRepository,
  @Value("\${pollers.pending-execution-service-agent.interval-ms:15000}") private val pollingIntervalMs: Long
) : AbstractPollingNotificationAgent(clusterLock) {

  private final val log = LoggerFactory.getLogger(PendingExecutionAgent::class.java)
  private final val lastCompletedCriteria = ExecutionRepository.ExecutionCriteria()
    .setPageSize(1)
    .setStatuses(ExecutionStatus.COMPLETED.map { it.toString() })
  private final val singleRunningCriteria = ExecutionRepository.ExecutionCriteria()
    .setPageSize(1)
    .setStatuses(ExecutionStatus.RUNNING)
  private final val kickCounter: Counter = registry.counter("pollers.pendingExecutionAgent.kickedExecutions")

  override fun getPollingInterval(): Long {
    return pollingIntervalMs
  }

  override fun getNotificationType(): String {
    return PendingExecutionAgent::class.java.simpleName
  }

  override fun tick() {
    try {
      val pendingConfigIds = pendingExecutionService.pendingIds()

      for (configId in pendingConfigIds) {
        val runningPipelines = executionRepository.retrievePipelinesForPipelineConfigId(configId, singleRunningCriteria)
          .toList().blockingGet()

        if (runningPipelines.isEmpty()) {
          val lastCompletedPipeline = executionRepository.retrievePipelinesForPipelineConfigId(configId, lastCompletedCriteria)
            .toList().blockingGet()

          val purgeQueue = if (lastCompletedPipeline.any()) {
            !(lastCompletedPipeline.first().isKeepWaitingPipelines)
          } else {
            false
          }
          queue.push(StartWaitingExecutions(configId, purgeQueue))

          log.info("Found pending execution(s) for pipeline {} with no running pipelines, kick-starting it with purge = {}", configId, purgeQueue)
          kickCounter.increment()
        }
      }
    } catch (e: Exception) {
      log.error("Agent {} failed to kick-start pending executions", javaClass, e)
    }
  }
}
