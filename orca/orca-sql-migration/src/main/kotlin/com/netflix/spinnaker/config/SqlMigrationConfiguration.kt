/*
 * Copyright 2025 DoorDash, Inc.
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

import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.orca.sql.SpringLiquibaseProxy
import liquibase.integration.spring.SpringLiquibase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Orca SQL schema migrations.
 *
 * This is not imported by [SqlConfiguration]. It is picked up only when this
 * module is on the classpath and `com.netflix.spinnaker.config` is scanned,
 * which is how Orca itself starts. Services that depend on orca-sql alone
 * persist executions without migrating the Orca schema.
 */
@Configuration
@ConditionalOnProperty("sql.enabled")
class SqlMigrationConfiguration {
  private val log: Logger = LoggerFactory.getLogger(SqlMigrationConfiguration::class.java)

  @Bean
  fun liquibase(properties: SqlProperties): SpringLiquibase {
    log.info("Creating Orca Liquibase bean for SQL migrations")
    return SpringLiquibaseProxy(properties)
  }
}
