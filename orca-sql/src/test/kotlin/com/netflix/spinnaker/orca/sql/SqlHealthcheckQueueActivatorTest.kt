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
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.JupiterTests
import com.oneeyedmen.minutest.junit.context
import org.jooq.DSLContext
import org.jooq.DeleteWhereStep
import org.jooq.Table
import strikt.api.expect
import strikt.assertions.isEqualTo

class SqlHealthcheckQueueActivatorTest : JupiterTests {

  override val tests = context<Unit> {

    val dslContext = mock<DSLContext>()
    val query = mock<DeleteWhereStep<*>>()

    after {
      reset(dslContext, query)
    }

    context("a healthy current state") {
      val subject = SqlHealthcheckActivator(dslContext, NoopRegistry(), unhealthyThreshold = 1).apply {
        _enabled.set(true)
      }

      test("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doThrow RuntimeException("oh no")

        subject.performWrite()
        subject.performWrite()

        expect(subject.enabled).isEqualTo(false)
      }
    }

    context("an unhealthy sql connection") {
      val subject = SqlHealthcheckActivator(dslContext, NoopRegistry(), healthyThreshold = 1).apply {
        _enabled.set(false)
      }

      test("successive write failures") {
        whenever(dslContext.delete(isA<Table<*>>())) doReturn query

        subject.performWrite()
        subject.performWrite()

        expect(subject.enabled).isEqualTo(true)
      }
    }
  }
}
