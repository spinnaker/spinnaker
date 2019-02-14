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

import org.jooq.SQLDialect
import java.util.concurrent.TimeUnit

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
