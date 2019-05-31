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
package com.netflix.spinnaker.sql

import com.netflix.spinnaker.kork.sql.config.ConnectionPoolProperties
import com.netflix.spinnaker.kork.sql.config.DataSourceFactory
import org.mariadb.jdbc.MariaDbPoolDataSource
import org.springframework.beans.factory.BeanCreationException
import java.sql.SQLException
import javax.sql.DataSource

class MariaDbDataSourceFactory(
  private val metricsExporter: MariaDbConnectionPoolMetricsExporter
) : DataSourceFactory {

  override fun build(poolName: String, connectionPoolProperties: ConnectionPoolProperties): DataSource {
    try {
      val dataSource = MariaDbPoolDataSource(connectionPoolProperties.jdbcUrl)
      dataSource.poolName = poolName
      dataSource.user = connectionPoolProperties.user
      dataSource.setPassword(connectionPoolProperties.password)

      metricsExporter.track(dataSource)

      return dataSource
    } catch (e: SQLException) {
      throw BeanCreationException("Failed creating pooled data source", e)
    }
  }
}
