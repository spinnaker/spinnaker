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

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

/**
 * Runs the request_id de-duplication statements from `20261001-task-ordering.yml`, read from the
 * changelog itself, against tasks that already have duplicate request IDs, as an existing install
 * would before upgrading.
 */
class TaskRequestIdMigrationTest {

  @Test
  fun `mysql keeps every task and the earliest duplicate's request ID`() {
    verify(SqlTestUtil.initTcMysqlDatabase()!!, "task-request-id-unique-mysql") { ctx ->
      ctx.execute("DROP INDEX task_request_id_uidx ON tasks")
    }
  }

  @Test
  fun `postgres keeps every task and the earliest duplicate's request ID`() {
    verify(SqlTestUtil.initTcPostgresDatabase()!!, "task-request-id-unique-postgresql") { ctx ->
      ctx.execute("DROP INDEX task_request_id_uidx")
    }
  }

  private fun verify(database: SqlTestUtil.TestDatabase, changeSetId: String, dropUniqueIndex: (DSLContext) -> Unit) {
    try {
      val ctx = database.context
      // Recreate the pre-upgrade state: no unique index, duplicate request IDs.
      dropUniqueIndex(ctx)
      listOf("01A" to "dup", "01B" to "dup", "01C" to "dup", "01D" to "solo").forEach { (id, requestId) ->
        ctx.insertInto(table("tasks"))
          .columns(field("id"), field("request_id"), field("owner_id"), field("created_at"))
          .values(id, requestId, "owner", 1L)
          .execute()
      }

      runChangeSet(ctx, changeSetId)
      // MySQL builds the unique index with a createIndex change rather than in the SQL.
      if (changeSetId.endsWith("mysql")) {
        ctx.execute("CREATE UNIQUE INDEX task_request_id_uidx ON tasks (request_id)")
      }

      val requestIds = ctx.select(field("id"), field("request_id"))
        .from(table("tasks"))
        .fetch()
        .associate { it.get(0, String::class.java).trim() to it.get(1, String::class.java) }
      assertThat(requestIds).isEqualTo(
        mapOf(
        "01A" to "dup",
        "01B" to "dup:duplicate:01B",
        "01C" to "dup:duplicate:01C",
        "01D" to "solo"
        )
      )

      // The changeset re-runs if its index build fails, so the de-duplication must be idempotent.
      runChangeSet(ctx, changeSetId, skipIndexCreation = true)
      assertThat(ctx.fetchCount(table("tasks"))).isEqualTo(4)
    } finally {
      SqlTestUtil.cleanupDb(database.context)
      database.close()
    }
  }

  private fun runChangeSet(ctx: DSLContext, changeSetId: String, skipIndexCreation: Boolean = false) {
    sqlStatements(changeSetId)
      .filter { !(skipIndexCreation && it.startsWith("CREATE")) }
      .forEach { ctx.execute(it) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun sqlStatements(changeSetId: String): List<String> {
    val changelog = Yaml().load<Map<String, Any>>(
      javaClass.classLoader.getResourceAsStream("db/changelog/20261001-task-ordering.yml")
    )
    val changeSet = (changelog["databaseChangeLog"] as List<Map<String, Any>>)
      .map { it["changeSet"] as Map<String, Any> }
      .first { it["id"] == changeSetId }
    return (changeSet["changes"] as List<Map<String, Any>>)
      .mapNotNull { (it["sql"] as Map<String, Any>?)?.get("sql") as String? }
      .flatMap { it.split(";") }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }
}
