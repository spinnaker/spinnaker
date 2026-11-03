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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.clouddriver.sql.exceptions.SqlUnavailableException
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientConnectionException
import java.sql.SQLTransientException
import java.time.Duration
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table

internal val tasksTable = table("tasks")
internal val taskStatesTable = table("task_states")
internal val taskResultsTable = table("task_results")
internal val taskOutputsTable = table("task_outputs")

internal val tasksFields = listOf("id", "request_id", "owner_id", "created_at").map { field(it) }
internal val taskStatesFields = listOf("id", "task_id", "created_at", "state", "phase", "status").map { field(it) }
internal val taskResultsFields = listOf("id", "task_id", "body").map { field(it) }
internal val taskOutputsFields = listOf("id", "task_id", "created_at", "manifest", "phase", "std_out", "std_error").map { field(it) }

/**
 * Retry behaviour for clouddriver-sql repositories, configured by `sql.retries` ([SqlRetryProperties]) like
 * cats-sql's `SqlCache`. Only transient failures (lost connections, deadlocks, timeouts) are retried; anything
 * else, such as a constraint violation, fails on the first attempt.
 */
class SqlRetries(properties: SqlRetryProperties = SqlRetryProperties()) {
  internal val transaction: Retry = retry("sqlTransaction", properties.transactions)
  internal val read: Retry = retry("sqlRead", properties.reads)

  private fun retry(name: String, properties: RetryProperties): Retry =
    Retry.of(
      name,
      RetryConfig.custom<Any>()
        .maxAttempts(properties.maxRetries)
        .waitDuration(Duration.ofMillis(properties.backoffMs))
        .retryOnException(::isTransient)
        .build()
    )
}

/**
 * Run the provided [fn] in a transaction, retrying transient failures per [retries].
 *
 * @throws SqlUnavailableException if the database is still unreachable once retries are exhausted
 */
internal fun DSLContext.transactional(retries: SqlRetries, fn: (DSLContext) -> Unit) {
  reportUnavailable {
    retries.transaction.executeRunnable {
      transaction { ctx ->
        fn(DSL.using(ctx))
      }
    }
  }
}

/**
 * Run the provided [fn], retrying transient failures per [retries].
 *
 * @throws SqlUnavailableException if the database is still unreachable once retries are exhausted
 */
internal fun <T> DSLContext.read(retries: SqlRetries, fn: (DSLContext) -> T): T =
  reportUnavailable {
    retries.read.executeSupplier { fn(this) }
  }

private inline fun <T> reportUnavailable(fn: () -> T): T =
  try {
    fn()
  } catch (e: Exception) {
    if (isConnectionFailure(e)) {
      throw SqlUnavailableException("Database unavailable after retries: ${e.message}", e)
    }
    throw e
  }

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

private fun isTransient(t: Throwable): Boolean =
  t.causes().any { it is SQLTransientException || it is SQLRecoverableException || it is SQLNonTransientConnectionException }

private fun isConnectionFailure(t: Throwable): Boolean =
  t.causes().any {
    it is SQLTransientConnectionException || it is SQLRecoverableException || it is SQLNonTransientConnectionException
  }
