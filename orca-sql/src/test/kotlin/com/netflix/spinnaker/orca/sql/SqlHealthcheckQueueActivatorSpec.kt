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

import com.netflix.spectator.api.NoopRegistry
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jooq.DSLContext
import org.jooq.DeleteWhereStep
import org.jooq.Table
import strikt.api.expect
import strikt.assertions.isEqualTo

object SqlHealthcheckQueueActivatorSpec : Spek({

  describe("healthchecking sql") {

    val dslContext = mock<DSLContext>()
    val query = mock<DeleteWhereStep<*>>()

    given("a healthy current state") {
      val subject = SqlHealthcheckActivator(dslContext, NoopRegistry(), unhealthyThreshold = 1).apply {
        _enabled.set(true)
      }

      afterEachTest { reset(dslContext, query) }

      on("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doThrow RuntimeException("oh no")

        subject.performWrite()
        subject.performWrite()

        it("deactivates its enabled flag") {
          expect(subject.enabled).isEqualTo(false)
        }
      }
    }

    given("an unhealthy sql connection") {
      val subject = SqlHealthcheckActivator(dslContext, NoopRegistry(), healthyThreshold = 1).apply {
        _enabled.set(false)
      }

      afterEachTest { reset(dslContext, query) }

      on("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doReturn query

        subject.performWrite()
        subject.performWrite()

        it("deactivates its enabled flag") {
          expect(subject.enabled).isEqualTo(true)
        }
      }

    }
  }
})
