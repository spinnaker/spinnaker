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

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sql")
data class SqlProperties(
  var migration: SqlMigrationProperties = SqlMigrationProperties(),
  var connectionPools: MutableMap<String, ConnectionPoolProperties> = mutableMapOf(),
  var retries: SqlRetryProperties = SqlRetryProperties(),

  @Deprecated("use named connection pools instead")
  var connectionPool: ConnectionPoolProperties? = null
) {

  fun getDefaultConnectionPoolProperties(): ConnectionPoolProperties {
    if (connectionPools.isEmpty()) {
      if (connectionPool == null) {
        throw MisconfiguredConnectionPoolsException.NEITHER_PRESENT
      }
      return connectionPool!!
    }
    if (connectionPools.size == 1) {
      return connectionPools.values.first()
    }
    return connectionPools.values.first { it.default }
  }
}

internal class MisconfiguredConnectionPoolsException(message: String) : IllegalStateException(message) {
  companion object {
    val NEITHER_PRESENT = MisconfiguredConnectionPoolsException(
      "Neither 'sql.connectionPools' nor 'sql.connectionPool' have been configured"
    )
    val BOTH_PRESENT = MisconfiguredConnectionPoolsException(
      "Both 'sql.connectionPools' and 'sql.connectionPool' are configured: Use only one"
    )
  }
}
