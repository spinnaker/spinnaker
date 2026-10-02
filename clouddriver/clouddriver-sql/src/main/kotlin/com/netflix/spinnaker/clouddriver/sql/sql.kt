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

import com.netflix.spinnaker.config.SqlRetryProperties
import java.sql.SQLTransientException
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.netflix.spinnaker.clouddriver.sql")

internal var sqlRetryProperties = SqlRetryProperties()

internal val tasksTable = table("tasks")
internal val taskStatesTable = table("task_states")
internal val taskResultsTable = table("task_results")
internal val taskOutputsTable = table("task_outputs")

internal val tasksFields = listOf("id", "request_id", "owner_id", "created_at").map { field(it) }
internal val taskStatesFields = listOf("id", "task_id", "created_at", "state", "phase", "status").map { field(it) }
internal val taskResultsFields = listOf("id", "task_id", "body").map { field(it) }
internal val taskOutputsFields = listOf("id", "task_id", "created_at", "manifest", "phase", "std_out", "std_error").map { field(it) }

/**
 * Run the provided [fn] in a transaction with retry.
 * Retry parameters are configured via `sql.retry.*` properties.
 */
internal fun DSLContext.transactional(fn: (DSLContext) -> Unit) {
  retryable("sqlTransaction") {
    transaction { ctx ->
      fn(DSL.using(ctx))
    }
  }
}

/**
 * Run the provided [fn] with retry.
 * Retry parameters are configured via `sql.retry.*` properties.
 */
internal fun <T> DSLContext.read(fn: (DSLContext) -> T): T {
  return retryable("sqlRead") {
    fn(this)
  }
}

private inline fun <T> retryable(label: String, block: () -> T): T {
  val props = sqlRetryProperties
  for (attempt in 1..props.maxRetryAttempts) {
    try {
      return block()
    } catch (e: Exception) {
      if (attempt == props.maxRetryAttempts || !isRetryable(e)) {
        throw e
      }
      log.warn("{} failed (attempt {}/{}): {}", label, attempt, props.maxRetryAttempts, e.message)
      Thread.sleep(props.waitDurationMs)
    }
  }
  throw IllegalStateException("retryable block for '$label' never executed — maxRetryAttempts must be >= 1, got ${props.maxRetryAttempts}")
}

private fun isRetryable(e: Exception): Boolean {
  var cause: Throwable? = e
  while (cause != null) {
    if (cause is SQLTransientException || cause is DataAccessException) return true
    cause = cause.cause
  }
  return false
}
