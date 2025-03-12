/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.DSLContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

internal class SqlConfigurationTests {

  @Nested
  @ExtendWith(SpringExtension::class)
  @ActiveProfiles("test", "twodialects")
  @SpringBootTest(
    classes = [SqlConfigTestApp::class]
  )
  @DisplayName("Two pools with different dialect.")
  class MultiDialectTest {

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var sqlProperties: SqlProperties

    @Test
    fun `should have 2 JOOQ configured one for each H2 and MySQL`() {
      expectThat(applicationContext.getBeansOfType(DSLContext::class.java).size).isEqualTo(2)
      expectThat(sqlProperties.connectionPools.size).isEqualTo(2)
      expectThat(applicationContext.getBean("jooq")).isNotNull()
      expectThat(applicationContext.getBean("jooq")).isA<DSLContext>()
      expectThat(applicationContext.getBean("secondaryJooq")).isNotNull()
      expectThat(applicationContext.getBean("secondaryJooq")).isA<DSLContext>()
      expectThat(applicationContext.getBean("liquibase")).isNotNull()
      expectThat(applicationContext.getBean("secondaryLiquibase")).isNotNull()
    }
  }

  @Nested
  @ExtendWith(SpringExtension::class)
  @ActiveProfiles("test", "singledialect")
  @SpringBootTest(
    classes = [SqlConfigTestApp::class]
  )
  @DisplayName("Two pools with single MYSQL(default) dialect.")
  class SingleDialectTest {

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var sqlProperties: SqlProperties

    @Test
    fun `should have 1 JOOQ configured for MYSQL`() {
      expectThat(applicationContext.getBeansOfType(DSLContext::class.java).size).isEqualTo(1)
      expectThat(sqlProperties.connectionPools.size).isEqualTo(2)
      expectThat(applicationContext.getBean("jooq")).isNotNull()
      expectThat(applicationContext.getBean("jooq")).isA<DSLContext>()
      expectThat(applicationContext.getBean("liquibase")).isNotNull()
    }
  }
}

@SpringBootApplication
@Import(PlatformComponents::class, DefaultSqlConfiguration::class)
internal class SqlConfigTestApp
