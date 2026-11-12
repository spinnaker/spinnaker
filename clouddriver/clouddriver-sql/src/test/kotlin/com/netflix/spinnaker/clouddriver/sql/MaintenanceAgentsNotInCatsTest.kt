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

import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.clouddriver.cache.MaintenanceAgent
import com.netflix.spinnaker.clouddriver.sql.event.SqlEventCleanupAgent
import com.netflix.spinnaker.config.SqlConfiguration
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

/**
 * The cleanup agents must be scheduled by the maintenance scheduler only, never handed to CATS
 * (which drops them when caching.write-enabled=false).
 */
class MaintenanceAgentsNotInCatsTest {

  @Test
  fun `cleanup agents are maintenance agents`() {
    expectThat(MaintenanceAgent::class.java.isAssignableFrom(SqlTaskCleanupAgent::class.java)).isTrue()
    expectThat(MaintenanceAgent::class.java.isAssignableFrom(SqlEventCleanupAgent::class.java)).isTrue()
  }

  @Test
  fun `cleanup agents are not SqlAgents so sqlAgentProvider cannot collect them`() {
    expectThat(SqlAgent::class.java.isAssignableFrom(SqlTaskCleanupAgent::class.java)).isFalse()
    expectThat(SqlAgent::class.java.isAssignableFrom(SqlEventCleanupAgent::class.java)).isFalse()
  }

  @Test
  fun `no SqlProvider bean wraps the cleanup agents`() {
    val providerBeans = SqlConfiguration::class.java.declaredMethods
      .filter { Provider::class.java.isAssignableFrom(it.returnType) }
    expectThat(providerBeans.isEmpty()).isTrue()
  }
}
