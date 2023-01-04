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

import io.github.resilience4j.retry.annotation.Retry
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
 * Run the provided [fn] in a transaction, retrying on failures using resilience4j.retry.instances.sqlTransaction
 * configuration.
 */
@Retry(name = "sqlTransaction")
internal fun DSLContext.transactional(fn: (DSLContext) -> Unit) {
  transaction { ctx ->
    fn(DSL.using(ctx))
  }
}

/**
 * Run the provided [fn], retrying on failures using resilience4j.retry.instances.sqlRead configuration.
 */
@Retry(name = "sqlRead")
internal fun <T> DSLContext.read(fn: (DSLContext) -> T): T {
  return fn(this)
}
