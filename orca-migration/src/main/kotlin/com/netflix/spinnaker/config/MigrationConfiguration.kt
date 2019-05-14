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
package com.netflix.spinnaker.config

import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.DualExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.migration.OrchestrationMigrationAgent
import com.netflix.spinnaker.orca.pipeline.persistence.migration.PipelineMigrationAgent
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Optional

@Configuration
class MigrationConfiguration {

  @Bean
  @ConditionalOnExpression("\${pollers.orchestration-migrator.enabled:false}")
  fun orchestrationMigrationAgent(
    clusterLock: NotificationClusterLock,
    front50Service: Front50Service,
    dualExecutionRepository: Optional<DualExecutionRepository>,
    @Value("\${pollers.orchestration-migrator.interval-ms:3600000}") pollIntervalMs: Long
  ): OrchestrationMigrationAgent {
    if (!dualExecutionRepository.isPresent) {
      throw BeanInitializationException("Orchestration migration enabled, but dualExecutionRepository has not been configured")
    }
    return OrchestrationMigrationAgent(clusterLock, front50Service, dualExecutionRepository.get(), pollIntervalMs)
  }

  @Bean
  @ConditionalOnExpression("\${pollers.pipeline-migrator.enabled:false}")
  fun pipelineMigrationAgent(
    clusterLock: NotificationClusterLock,
    front50Service: Front50Service,
    dualExecutionRepository: Optional<DualExecutionRepository>,
    @Value("\${pollers.pipeline-migrator.interval-ms:3600000}") pollIntervalMs: Long
  ): PipelineMigrationAgent {
    if (!dualExecutionRepository.isPresent) {
      throw BeanInitializationException("Pipeline migration enabled, but dualExecutionRepository has not been configured")
    }
    return PipelineMigrationAgent(clusterLock, front50Service, dualExecutionRepository.get(), pollIntervalMs)
  }
}
