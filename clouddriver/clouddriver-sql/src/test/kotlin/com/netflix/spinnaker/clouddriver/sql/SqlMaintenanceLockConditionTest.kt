/*
 * Copyright 2026 DoorDash, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.clouddriver.cache.MaintenanceLock
import com.netflix.spinnaker.config.SqlConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * The lock's constructor runs DDL when `sql.table-namespace` is set, so pods with no maintenance work
 * (read-only or maintenance-agents.enabled=false) must not create it.
 */
class SqlMaintenanceLockConditionTest {

  private val jooq = mockk<DSLContext>(relaxed = true).also {
    every { it.dialect() } returns SQLDialect.MYSQL
  }

  // Keep only the lock, its factory (SqlConfiguration) and the DSLContext it needs; the rest of
  // SqlConfiguration is not under test.
  private val keepOnlyLock = BeanFactoryPostProcessor { beanFactory ->
    val registry = beanFactory as BeanDefinitionRegistry
    registry.beanDefinitionNames
      .filter { it !in setOf("sqlConfiguration", "sqlMaintenanceLock", "jooq") }
      .forEach { registry.removeBeanDefinition(it) }
  }

  private fun runner(vararg properties: String) = ApplicationContextRunner()
    .withBean("jooq", DSLContext::class.java, { jooq })
    .withInitializer { it.addBeanFactoryPostProcessor(keepOnlyLock) }
    .withUserConfiguration(SqlConfiguration::class.java)
    .withPropertyValues("sql.enabled=true", "sql.table-namespace=ns1", *properties)

  private fun lockBeans(context: ApplicationContext) =
    context.getBeansOfType(MaintenanceLock::class.java).size

  @Test
  fun `read-only does not create the lock or run DDL`() {
    runner("sql.read-only=true").run {
      expectThat(lockBeans(it)).isEqualTo(0)
      verify(exactly = 0) { jooq.execute(any<String>()) }
    }
  }

  @Test
  fun `maintenance agents disabled does not create the lock or run DDL`() {
    runner("maintenance-agents.enabled=false").run {
      expectThat(lockBeans(it)).isEqualTo(0)
      verify(exactly = 0) { jooq.execute(any<String>()) }
    }
  }

  @Test
  fun `default writable context creates the lock and its namespaced table`() {
    runner().run {
      expectThat(lockBeans(it)).isEqualTo(1)
      verify(exactly = 1) {
        jooq.execute("CREATE TABLE IF NOT EXISTS cats_agent_locks_ns1 LIKE cats_agent_locks")
      }
    }
  }
}
