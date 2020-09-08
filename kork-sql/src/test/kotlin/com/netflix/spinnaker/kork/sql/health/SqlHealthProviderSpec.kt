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
package com.netflix.spinnaker.kork.sql.health

import com.netflix.spectator.api.NoopRegistry
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jooq.DSLContext
import org.jooq.DeleteUsingStep
import org.jooq.Table
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object SqlHealthProviderSpec : Spek({

  Feature("healthchecking sql") {

    val dslContext = mock<DSLContext>()
    val query = mock<DeleteUsingStep<*>>()

    Scenario("a healthy current state") {
      val subject = SqlHealthProvider(dslContext, NoopRegistry(), readOnly = false, unhealthyThreshold = 1).apply {
        _enabled.set(true)
      }

      afterEachStep { reset(dslContext, query) }

      When("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doThrow RuntimeException("oh no")

        subject.performCheck()
        subject.performCheck()
      }

      Then("deactivates its enabled flag") {
        expectThat(subject.enabled).isEqualTo(false)
      }
    }

    Scenario("a healthy current readOnly state") {
      val subject = SqlHealthProvider(dslContext, NoopRegistry(), readOnly = true, unhealthyThreshold = 1).apply {
        _enabled.set(true)
      }

      afterEachStep { reset(dslContext, query) }

      When("successive read failures") {
        whenever(dslContext.select()) doThrow RuntimeException("oh no")

        subject.performCheck()
        subject.performCheck()
      }

      Then("deactivates its enabled flag") {
        expectThat(subject.enabled).isEqualTo(false)
      }
    }

    Scenario("an unhealthy sql connection") {
      val subject = SqlHealthProvider(dslContext, NoopRegistry(), readOnly = false, healthyThreshold = 1).apply {
        _enabled.set(false)
      }

      afterEachStep { reset(dslContext, query) }

      When("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doReturn query

        subject.performCheck()
        subject.performCheck()
      }

      Then("deactivates its enabled flag") {
        expectThat(subject.enabled).isEqualTo(true)
      }
    }
  }
})
