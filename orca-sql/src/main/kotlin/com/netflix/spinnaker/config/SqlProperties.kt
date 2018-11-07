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

import org.jooq.SQLDialect
import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * @param migration Defines the connection info for the migration user
 * @param connectionPool The connection pool information for runtime SQL functionality
 * @param transactionRetry Defines internal retry policies when connectivity to SQL is dodgy
 * @param partitionName Multi-region partitioning; unused presently
 * @param batchReadSize Defines the internal page size for large select scans
 */
@ConfigurationProperties("sql")
data class SqlProperties(
  var migration: MigrationProperties = MigrationProperties(),
  var connectionPool: ConnectionPoolProperties = ConnectionPoolProperties(),
  var transactionRetry: TransactionRetryProperties = TransactionRetryProperties(),
  var partitionName: String? = null,
  var batchReadSize: Int = 10
)

data class MigrationProperties(
  var jdbcUrl: String = "jdbc:mysql://localhost/orca",
  var user: String? = null,
  var password: String? = null,
  var driver: String? = null,
  var additionalChangeLogs: List<String> = mutableListOf()
)

data class ConnectionPoolProperties(
  var dialect: SQLDialect = SQLDialect.MYSQL,
  var jdbcUrl: String = "jdbc:mysql://localhost/orca",
  var driver: String? = null,
  var user: String? = null,
  var password: String? = null,
  var connectionTimeoutMs: Long = SECONDS.toMillis(5),
  var validationTimeoutMs: Long = SECONDS.toMillis(5),
  var idleTimeoutMs: Long = MINUTES.toMillis(1),
  var maxLifetimeMs: Long = SECONDS.toMillis(30),
  var minIdle: Int = 5,
  var maxPoolSize: Int = 20
)

data class TransactionRetryProperties(
  var maxRetries: Int = 5,
  var backoffMs: Long = 100
)
