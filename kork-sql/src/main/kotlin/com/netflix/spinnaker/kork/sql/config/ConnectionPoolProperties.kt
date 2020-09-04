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
package com.netflix.spinnaker.kork.sql.config

import java.util.concurrent.TimeUnit
import org.jooq.SQLDialect

/**
 * Configuration properties for SQL connection pools.
 *
 * @param dialect The SQL dialect the connections will use
 * @param jdbcUrl The JDBC URL used to connect to the database
 * @param driver The JDBC driver name
 * @param user The user to connect to the database with
 * @param password The password to authenticate the [user]
 * @param connectionTimeoutMs Max time to wait before timing out when connecting to the database
 * @param validationTimeoutMs Max time the pool will wait for a connection to be validated as alive
 * @param idleTimeoutMs Controls the maximum amount of time that a connection is allowed to sit idle in the pool
 * @param maxLifetimeMs Controls the maximum amount of time a connection will exist in the pool.
 * @param minIdle The minimum number of idle connections to maintain to the database
 * @param maxPoolSize The maximum number of connections to keep in the pool
 * @param default Whether or not this connection pool should be used as the default pool within the application
 */
@Suppress("MagicNumber")
data class ConnectionPoolProperties(
  var dialect: SQLDialect = SQLDialect.MYSQL,
  var jdbcUrl: String? = null,
  var driver: String? = null,
  var user: String? = null,
  var password: String? = null,
  var connectionTimeoutMs: Long = TimeUnit.SECONDS.toMillis(5),
  var validationTimeoutMs: Long = TimeUnit.SECONDS.toMillis(5),
  var idleTimeoutMs: Long = TimeUnit.MINUTES.toMillis(1),
  var maxLifetimeMs: Long = TimeUnit.SECONDS.toMillis(30),
  var minIdle: Int = 5,
  var maxPoolSize: Int = 20,
  var default: Boolean = false
)
