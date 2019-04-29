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
package com.netflix.spinnaker.kork.sql.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import javax.sql.DataSource

class HikariDataSourceFactory(
  private val metricsTrackerFactory: MetricsTrackerFactory
) : DataSourceFactory {

  override fun build(poolName: String, connectionPoolProperties: ConnectionPoolProperties): DataSource =
    HikariDataSource(hikariConfig(poolName, metricsTrackerFactory, connectionPoolProperties))

  private fun hikariConfig(
    poolName: String,
    metricsTrackerFactory: MetricsTrackerFactory,
    connectionPool: ConnectionPoolProperties
  ): HikariConfig =
    HikariConfig().apply {
      this.poolName = poolName
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
      setMetricsTrackerFactory(metricsTrackerFactory)
    }
}
