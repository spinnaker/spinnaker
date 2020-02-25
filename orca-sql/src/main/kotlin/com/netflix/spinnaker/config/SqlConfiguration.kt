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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy
import com.netflix.spinnaker.orca.interlink.Interlink
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.notifications.SqlNotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.sql.SpringLiquibaseProxy
import com.netflix.spinnaker.orca.sql.SqlHealthIndicator
import com.netflix.spinnaker.orca.sql.SqlHealthcheckActivator
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import liquibase.integration.spring.SpringLiquibase
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.util.Optional

@Configuration
@ConditionalOnProperty("sql.enabled")
@EnableConfigurationProperties(OrcaSqlProperties::class)
@Import(DefaultSqlConfiguration::class)
@ComponentScan("com.netflix.spinnaker.orca.sql")

class SqlConfiguration {
  @Bean
  fun liquibase(properties: SqlProperties): SpringLiquibase =
    SpringLiquibaseProxy(properties)

  @ConditionalOnProperty("execution-repository.sql.enabled")
  @Bean fun sqlExecutionRepository(
    dsl: DSLContext,
    mapper: ObjectMapper,
    registry: Registry,
    properties: SqlProperties,
    orcaSqlProperties: OrcaSqlProperties,
    interlink: Optional<Interlink>
  ) =
    SqlExecutionRepository(
      orcaSqlProperties.partitionName,
      dsl,
      mapper,
      properties.retries.transactions,
      orcaSqlProperties.batchReadSize,
      orcaSqlProperties.stageReadSize,
      interlink = interlink.orElse(null)
    ).let {
      InstrumentedProxy.proxy(registry, it, "sql.executions", mapOf(Pair("repository", "primary"))) as ExecutionRepository
    }

  @ConditionalOnProperty("execution-repository.sql.enabled", "execution-repository.sql.secondary.enabled")
  @Bean fun secondarySqlExecutionRepository(
    dsl: DSLContext,
    mapper: ObjectMapper,
    registry: Registry,
    properties: SqlProperties,
    orcaSqlProperties: OrcaSqlProperties,
    @Value("\${execution-repository.sql.secondary.pool-name}") poolName: String
  ) =
    SqlExecutionRepository(
      orcaSqlProperties.partitionName,
      dsl,
      mapper,
      properties.retries.transactions,
      orcaSqlProperties.batchReadSize,
      orcaSqlProperties.stageReadSize,
      poolName
    ).let {
      InstrumentedProxy.proxy(registry, it, "sql.executions", mapOf(Pair("repository", "secondary"))) as ExecutionRepository
    }

  @Bean fun sqlHealthcheckActivator(dsl: DSLContext, registry: Registry) =
    SqlHealthcheckActivator(dsl, registry)

  @Bean("dbHealthIndicator") fun dbHealthIndicator(
    sqlHealthcheckActivator: SqlHealthcheckActivator,
    sqlProperties: SqlProperties,
    dynamicConfigService: DynamicConfigService
  ) =
    SqlHealthIndicator(sqlHealthcheckActivator, sqlProperties.getDefaultConnectionPoolProperties().dialect, dynamicConfigService)

  @ConditionalOnProperty("execution-repository.sql.enabled")
  @ConditionalOnMissingBean(NotificationClusterLock::class)
  @Primary
  @Bean
  fun sqlNotificationClusterLock(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties
  ) = SqlNotificationClusterLock(
    jooq = jooq,
    clock = clock,
    retryProperties = properties.retries.transactions
  )
}
