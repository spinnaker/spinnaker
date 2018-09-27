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
package com.netflix.spinnaker.kork.sql

import com.netflix.spinnaker.kork.PlatformComponents
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.health.SqlHealthIndicator
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit4.SpringRunner
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [StartupTestApp::class],
  webEnvironment = SpringBootTest.WebEnvironment.NONE,
  properties = [
    "sql.enabled=true",
    "sql.migration.jdbcUrl=jdbc:h2:mem:test",
    "sql.migration.dialect=H2",
    "sql.connectionPool.jdbcUrl=jdbc:h2:mem:test",
    "sql.connectionPool.dialect=H2"
  ]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var dbHealthIndicator: HealthIndicator

  @Autowired
  lateinit var jooq: DSLContext

  @Test
  fun `uses SqlHealthIndicator`() {
    expectThat(dbHealthIndicator).isA<SqlHealthIndicator>()

    expectThat(
      jooq
        .insertInto(table("healthcheck"), listOf(field("id")))
        .values(true).execute()
    ).isEqualTo(1)
  }
}

@SpringBootApplication
@Import(PlatformComponents::class, DefaultSqlConfiguration::class)
internal class StartupTestApp
