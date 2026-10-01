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

import com.netflix.spinnaker.config.SqlRetryProperties
import io.mockk.every
import io.mockk.mockk
import org.jooq.DSLContext
import org.jooq.TransactionalRunnable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import java.sql.SQLTransientConnectionException

class SqlRetryTest {

  private lateinit var savedProperties: SqlRetryProperties

  @BeforeEach
  fun setUp() {
    savedProperties = sqlRetryProperties
    sqlRetryProperties = SqlRetryProperties().apply {
      maxRetryAttempts = 3
      waitDurationMs = 0
    }
  }

  @AfterEach
  fun tearDown() {
    sqlRetryProperties = savedProperties
  }

  @Test
  fun `transactional retries on DataAccessException and succeeds`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    every { ctx.transaction(any<TransactionalRunnable>()) } answers {
      callCount++
      if (callCount < 3) {
        throw org.jooq.exception.DataAccessException("deadlock")
      }
      firstArg<TransactionalRunnable>().run(mockk(relaxed = true))
    }

    ctx.transactional { }

    expectThat(callCount).isEqualTo(3)
  }

  @Test
  fun `transactional retries on SQLTransientException and succeeds`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    every { ctx.transaction(any<TransactionalRunnable>()) } answers {
      callCount++
      if (callCount < 2) {
        throw org.jooq.exception.DataAccessException(
          "connection lost",
          SQLTransientConnectionException("connection reset")
        )
      }
      firstArg<TransactionalRunnable>().run(mockk(relaxed = true))
    }

    ctx.transactional { }

    expectThat(callCount).isEqualTo(2)
  }

  @Test
  fun `transactional throws after exhausting retries`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    every { ctx.transaction(any<TransactionalRunnable>()) } answers {
      callCount++
      throw org.jooq.exception.DataAccessException("deadlock")
    }

    expectThrows<org.jooq.exception.DataAccessException> {
      ctx.transactional { }
    }

    expectThat(callCount).isEqualTo(3)
  }

  @Test
  fun `transactional does not retry non-retryable exceptions`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    every { ctx.transaction(any<TransactionalRunnable>()) } answers {
      callCount++
      throw IllegalStateException("bad data")
    }

    expectThrows<IllegalStateException> {
      ctx.transactional { }
    }

    expectThat(callCount).isEqualTo(1)
  }

  @Test
  fun `read retries on DataAccessException and succeeds`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    val result = ctx.read {
      callCount++
      if (callCount < 3) {
        throw org.jooq.exception.DataAccessException("deadlock")
      }
      "ok"
    }

    expectThat(result).isEqualTo("ok")
    expectThat(callCount).isEqualTo(3)
  }

  @Test
  fun `read throws after exhausting retries`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    expectThrows<org.jooq.exception.DataAccessException> {
      ctx.read {
        callCount++
        throw org.jooq.exception.DataAccessException("deadlock")
      }
    }

    expectThat(callCount).isEqualTo(3)
  }

  @Test
  fun `retry respects configured maxRetryAttempts`() {
    sqlRetryProperties = SqlRetryProperties().apply {
      maxRetryAttempts = 5
      waitDurationMs = 0
    }

    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    expectThrows<org.jooq.exception.DataAccessException> {
      ctx.read {
        callCount++
        throw org.jooq.exception.DataAccessException("deadlock")
      }
    }

    expectThat(callCount).isEqualTo(5)
  }

  @Test
  fun `retries when cause chain contains a retryable exception`() {
    val ctx = mockk<DSLContext>(relaxed = true)
    var callCount = 0

    val result = ctx.read {
      callCount++
      if (callCount < 2) {
        throw RuntimeException(
          "wrapper",
          SQLTransientConnectionException("connection reset")
        )
      }
      "ok"
    }

    expectThat(result).isEqualTo("ok")
    expectThat(callCount).isEqualTo(2)
  }
}
