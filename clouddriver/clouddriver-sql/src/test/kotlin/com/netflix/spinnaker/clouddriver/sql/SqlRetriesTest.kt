/*
 * Copyright 2026 McIntosh.farm
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.sql.exceptions.SqlUnavailableException
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.api.exceptions.ExceptionMessage
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLTransientConnectionException
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Runs the clouddriver-sql repositories against a jOOQ [MockConnection] that fails the way a real
 * JDBC driver does, so the retry and 503 behaviour is exercised end to end rather than inferred from
 * configuration.
 */
class SqlRetriesTest {

  private val retries = SqlRetries(SqlRetryProperties(RetryProperties(3, 1), RetryProperties(3, 1)))

  @Test
  fun `a read that fails transiently is retried until it succeeds`() {
    val attempts = AtomicInteger()
    val repository = taskRepository(
      dsl { if (attempts.incrementAndGet() < 3) throw SQLTransientConnectionException("connection reset") else emptyResult() }
    )

    assertThat(repository.getByClientRequestId("request")).isNull()
    assertThat(attempts.get()).isEqualTo(3)
  }

  @Test
  fun `a transaction that fails transiently is retried until it succeeds`() {
    val attempts = AtomicInteger()
    val jooq = dsl { if (attempts.incrementAndGet() < 3) throw SQLTransientConnectionException("connection reset") else emptyResult() }

    jooq.transactional(retries) { ctx -> ctx.execute("update tasks set owner_id = 'x'") }

    assertThat(attempts.get()).isEqualTo(3)
  }

  @Test
  fun `a database that stays unreachable is reported as 503 once retries are exhausted`() {
    val attempts = AtomicInteger()
    val repository = taskRepository(
      dsl {
        attempts.incrementAndGet()
        throw SQLTransientConnectionException("connection refused")
      }
    )

    val failure = runCatching { repository.getByClientRequestId("request") }.exceptionOrNull()

    assertThat(failure).isInstanceOf(SqlUnavailableException::class.java)
    assertThat(attempts.get()).isEqualTo(3)
    val response = MockHttpServletResponse()
    GenericExceptionHandlers(ExceptionMessageDecorator(mock<ObjectProvider<List<ExceptionMessage>>>()))
      .handleException(failure as Exception, response, MockHttpServletRequest())
    assertThat(response.status).isEqualTo(503)
  }

  @Test
  fun `a non-transient failure is not retried and is not reported as unavailable`() {
    val attempts = AtomicInteger()
    val repository = taskRepository(
      dsl {
        attempts.incrementAndGet()
        throw SQLIntegrityConstraintViolationException("duplicate key")
      }
    )

    assertThatThrownBy { repository.getByClientRequestId("request") }
      .isInstanceOf(DataAccessException::class.java)
      .isNotInstanceOf(SqlUnavailableException::class.java)
    assertThat(attempts.get()).isEqualTo(1)
  }

  private fun taskRepository(jooq: DSLContext) =
    SqlTaskRepository(jooq, ObjectMapper(), Clock.systemUTC(), ConnectionPools.TASKS.value, retries)

  private fun dsl(execute: () -> Array<MockResult>): DSLContext =
    DSL.using(MockConnection(MockDataProvider { execute() }), SQLDialect.MYSQL)

  // A typed, empty result: the task lookup selects a single `id` column and finds no rows.
  private fun emptyResult(): Array<MockResult> =
    arrayOf(MockResult(0, DSL.using(SQLDialect.MYSQL).newResult(DSL.field("id", String::class.java))))
}
