/*
 * Copyright 2020 Netflix, Inc.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.peering.ExecutionCopier
import com.netflix.spinnaker.orca.peering.PeeringAgent
import com.netflix.spinnaker.orca.peering.MySqlRawAccess
import com.netflix.spinnaker.orca.peering.PeeringMetrics
import com.netflix.spinnaker.orca.peering.SqlRawAccess
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors
import javax.naming.ConfigurationException

@Configuration
/**
 * TODO(mvulfson): this needs to support multiple (arbitrary number of) beans / peers defined in config
 * We can do something similar what kork does for sql connection pools
 */
@EnableConfigurationProperties(PeeringAgentConfigurationProperties::class)
class PeeringAgentConfiguration {
  @Bean
  @ConditionalOnExpression("\${pollers.peering.enabled:false}")
  fun peeringAgent(
    jooq: DSLContext,
    clusterLock: NotificationClusterLock,
    dynamicConfigService: DynamicConfigService,
    registry: Registry,
    properties: PeeringAgentConfigurationProperties
  ): PeeringAgent {
    if (properties.peerId == null || properties.poolName == null) {
      throw ConfigurationException("pollers.peering.id and pollers.peering.poolName must be specified for peering")
    }

    val executor = Executors.newCachedThreadPool(
      ThreadFactoryBuilder()
        .setNameFormat(PeeringAgent::class.java.simpleName + "-${properties.peerId}-%d")
        .build())

    val sourceDB: SqlRawAccess
    val destinationDB: SqlRawAccess

    when (jooq.dialect()) {
      SQLDialect.MYSQL -> {
        sourceDB = MySqlRawAccess(jooq, properties.poolName!!, properties.chunkSize)
        destinationDB = MySqlRawAccess(jooq, "default", properties.chunkSize)
      }
      else -> throw UnsupportedOperationException("Peering only supported on MySQL right now")
    }

    val metrics = PeeringMetrics(properties.peerId!!, registry)
    val copier = ExecutionCopier(properties.peerId!!, sourceDB, destinationDB, executor, properties.threadCount, properties.chunkSize, metrics)

    return PeeringAgent(
      properties.peerId!!,
      properties.intervalMs,
      properties.clockDriftMs,
      sourceDB,
      destinationDB,
      dynamicConfigService,
      metrics,
      copier,
      clusterLock)
  }
}
