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
package com.netflix.spinnaker.orca.sql

import org.jooq.SQLDialect
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health

/**
 * Overrides the default Spring DataSourceHealthIndicator, which perfroms
 * queries at request-time (bad). Uses the background agent of
 * [SqlHealthcheckActivator] to determine if SQL connections are healthy.
 */
class SqlHealthIndicator(
  private val sqlHealthcheckActivator: SqlHealthcheckActivator,
  private val sqlDialect: SQLDialect
) : AbstractHealthIndicator() {

  override fun doHealthCheck(builder: Health.Builder) {
    if (sqlHealthcheckActivator.enabled) {
      builder.up().withDetail("database", sqlDialect.name)
    } else {
      builder.down().withDetail("database", sqlDialect.name).let {
        sqlHealthcheckActivator.healthException?.let { exception ->
          it.withException(exception)
        }
      }
    }
  }
}
