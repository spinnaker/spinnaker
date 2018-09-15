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
package com.netflix.spinnaker.orca.sql.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SqlProperties
import com.netflix.spinnaker.orca.sql.telemetry.SpectatorHikariMetricsTrackerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

@Configuration
@ConditionalOnMissingBean(DataSource::class)
class DataSourceConfiguration {

  @Bean
  fun spectatorMetricsTrackerFactory(registry: Registry): MetricsTrackerFactory =
    SpectatorHikariMetricsTrackerFactory(registry)

  @DependsOn("liquibase")
  @Bean fun hikariConfig(metricsTrackerFactory: MetricsTrackerFactory, properties: SqlProperties): HikariConfig =
    HikariConfig().apply {
      properties.apply {
        jdbcUrl = connectionPool.jdbcUrl
        username = connectionPool.user
        password = connectionPool.password
        connectionTimeout = connectionPool.connectionTimeoutMs
        validationTimeout = connectionPool.validationTimeoutMs
        idleTimeout = connectionPool.idleTimeoutMs
        maxLifetime = connectionPool.maxLifetimeMs
        minimumIdle = connectionPool.minIdle
        maximumPoolSize = connectionPool.maxPoolSize
        if (connectionPool.driver != null) {
          driverClassName = connectionPool.driver
        }
      }
      setMetricsTrackerFactory(metricsTrackerFactory)
    }

  @Bean(destroyMethod = "close") fun dataSource(hikariConfig: HikariConfig): DataSource =
    HikariDataSource(hikariConfig)
}
