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
package com.netflix.spinnaker.orca.sql.telemetry

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.sql.pipeline.persistence.ExecutionStatisticsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnProperty("monitor.activeExecutions.redis", havingValue = "false")
class SqlActiveExecutionsMonitor(
  private val executionRepository: ExecutionStatisticsRepository,
  registry: Registry,
  @Value("\${monitor.activeExecutions.refresh.frequency.ms:60000}") refreshFrequencyMs: Long
) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val executor = Executors.newScheduledThreadPool(1)

  private val activePipelineCounter = registry.gauge(
    registry.createId("executions.active").withTag("executionType", PIPELINE.toString()),
    AtomicInteger(0)
  )

  private val activeOrchestrationCounter = registry.gauge(
    registry.createId("executions.active").withTag("executionType", ORCHESTRATION.toString()),
    AtomicInteger(0)
  )

  init {
    executor.scheduleWithFixedDelay(
      {
        try {
          refreshGauges()
        } catch (e : Exception) {
          log.error("Unable to refresh active execution gauges", e)
        }
      },
      0,
      refreshFrequencyMs,
      TimeUnit.MILLISECONDS
    )
  }

  fun refreshGauges() {
    executionRepository.countActiveExecutions().run {
      log.info("Refreshing active execution gauges (active: ${total()})")

      activePipelineCounter.set(pipelines)
      activeOrchestrationCounter.set(orchestrations)
    }
  }
}
