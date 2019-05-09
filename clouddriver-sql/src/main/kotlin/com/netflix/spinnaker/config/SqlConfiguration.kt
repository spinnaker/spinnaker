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
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.sql.SqlProvider
import com.netflix.spinnaker.clouddriver.sql.SqlTaskCleanupAgent
import com.netflix.spinnaker.clouddriver.sql.SqlTaskRepository
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock

@Configuration
@ConditionalOnProperty("sql.enabled")
@Import(DefaultSqlConfiguration::class)
@EnableConfigurationProperties(SqlTaskCleanupAgentProperties::class)
class SqlConfiguration {

  @Bean
  @ConditionalOnProperty("sql.task-repository.enabled")
  fun sqlTaskRepository(jooq: DSLContext,
                        clock: Clock,
                        sqlProperties: SqlProperties): TaskRepository =
    SqlTaskRepository(jooq, ObjectMapper(), clock, sqlProperties.retries)

  @Bean
  @ConditionalOnProperty("sql.task-repository.enabled")
  @ConditionalOnExpression("\${sql.read-only:false} == false")
  fun sqlTaskCleanupAgent(jooq: DSLContext,
                          clock: Clock,
                          registry: Registry,
                          properties: SqlTaskCleanupAgentProperties,
                          sqlProperties: SqlProperties): SqlTaskCleanupAgent =
    SqlTaskCleanupAgent(jooq, clock, registry, properties, sqlProperties.retries)

  @Bean
  @ConditionalOnProperty("sql.task-repository.enabled")
  @ConditionalOnExpression("\${sql.read-only:false} == false")
  fun sqlProvider(sqlTaskCleanupAgent: SqlTaskCleanupAgent): SqlProvider =
    SqlProvider(mutableListOf(sqlTaskCleanupAgent))
}
