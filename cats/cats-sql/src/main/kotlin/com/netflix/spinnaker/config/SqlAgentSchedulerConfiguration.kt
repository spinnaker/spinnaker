/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.sql.cluster.SqlClusteredAgentScheduler
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["caching.write-enabled"], matchIfMissing = true)
class SqlAgentSchedulerConfiguration {

  @Bean
  @ConditionalOnProperty(value = [
    "sql.enabled",
    "sql.scheduler.enabled"
  ])
  fun sqlAgentScheduler(
    jooq: DSLContext,
    agentIntervalProvider: AgentIntervalProvider,
    nodeStatusProvider: NodeStatusProvider,
    dynamicConfigService: DynamicConfigService,
    @Value("\${sql.table-namespace:#{null}}") tableNamespace: String?,
    sqlAgentProperties: SqlAgentProperties
  ): AgentScheduler<*> {
    return SqlClusteredAgentScheduler(
      jooq = jooq,
      nodeIdentity = DefaultNodeIdentity(),
      intervalProvider = agentIntervalProvider,
      nodeStatusProvider = nodeStatusProvider,
      dynamicConfigService = dynamicConfigService,
      enabledAgentPattern = sqlAgentProperties.enabledPattern,
      tableNamespace = tableNamespace,
      agentLockAcquisitionIntervalSeconds = sqlAgentProperties.agentLockAcquisitionIntervalSeconds
    )
  }
}
