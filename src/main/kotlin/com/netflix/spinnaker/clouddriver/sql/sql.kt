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

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table

internal val retrySupport = RetrySupport()

internal val tasksTable = table("tasks")
internal val taskStatesTable = table("task_states")
internal val taskResultsTable = table("task_results")

internal val tasksFields = listOf("id", "request_id", "owner_id", "created_at").map { field(it) }
internal val taskStatesFields = listOf("id", "task_id", "created_at", "state", "phase", "status").map { field(it) }
internal val taskResultsFields = listOf("id", "task_id", "body").map { field(it) }

/**
 * Run the provided [fn] in a transaction.
 */
internal fun DSLContext.transactional(retryProperties: RetryProperties, fn: (DSLContext) -> Unit) {
  retrySupport.retry({
    transaction { ctx ->
      fn(DSL.using(ctx))
    }
  }, retryProperties.maxRetries, retryProperties.backoffMs, false)
}

/**
 * Run the provided [fn] with retry support.
 */
internal fun <T> DSLContext.withRetry(retryProperties: RetryProperties, fn: (DSLContext) -> T): T {
  return retrySupport.retry({
    fn(this)
  }, retryProperties.maxRetries, retryProperties.backoffMs, false)
}
