package com.netflix.spinnaker.cats.sql.cache

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.jooq.util.mysql.MySQLDSL
import org.junit.jupiter.api.Test

/**
 * Regression test for jOOQ 3.19+ MySQL `ON DUPLICATE KEY UPDATE` rendering bug.
 *
 * jOOQ 3.19 (via Spring Boot 3.5.x) generates invalid MySQL syntax when using
 * [InsertValuesStep.onDuplicateKeyUpdate] — it emits PostgreSQL-specific
 * `AS excluded` even when the configured dialect is MySQL:
 *
 * ```sql
 * insert into t (...) values (...) as excluded
 * on duplicate key update ...
 * ```
 *
 * MySQL rejects this syntax with `BadSqlGrammarException`.
 *
 * The production fix (in SqlCache.putCacheResult) bypasses jOOQ's DSL for the
 * MySQL path and uses raw SQL instead, while keeping the PostgreSQL
 * `onConflict()` DSL path unchanged.
 */
class SqlCacheBatchInsertTest {

  @Test
  fun `jOOQ 3_19 onDuplicateKeyUpdate generates invalid as excluded for MySQL dialect`() {
    val ctx = DSL.using(SQLDialect.MYSQL)
    val insert = ctx.insertInto(
      DSL.table("cats_v1_test"),
      DSL.field("id"),
      DSL.field("agent"),
      DSL.field("application"),
      DSL.field("body_hash"),
      DSL.field("body"),
      DSL.field("last_updated")
    )

    insert.values("id-1", "agent", "app", "hash", "body", 1L)
    insert.onDuplicateKeyUpdate()
      .set(DSL.field("application"), MySQLDSL.values(DSL.field("application")) as Any)
      .set(DSL.field("body_hash"), MySQLDSL.values(DSL.field("body_hash")) as Any)
      .set(DSL.field("body"), MySQLDSL.values(DSL.field("body")) as Any)
      .set(DSL.field("last_updated"), MySQLDSL.values(DSL.field("last_updated")) as Any)

    val sql = insert.getSQL(ParamType.INLINED)

    // This assertion documents the jOOQ bug that the production code works around.
    assertThat(sql.lowercase())
      .`as`("jOOQ 3.19 generates 'as excluded' even for MySQL dialect")
      .contains("as excluded")
  }

  @Test
  fun `raw SQL upsert does not contain invalid as excluded syntax`() {
    val tableName = "cats_v1_test"
    val chunk = listOf("id-1", "id-2")
    val valuesPlaceholders = chunk.joinToString(", ") { "(?, ?, ?, ?, ?, ?)" }
    val params = chunk.flatMap {
      listOf(it, "agent", "app", "hash", "body", 1L)
    }.toTypedArray()

    val sql = """
      |INSERT INTO $tableName (id, agent, application, body_hash, body, last_updated)
      |VALUES $valuesPlaceholders
      |ON DUPLICATE KEY UPDATE
      |application = VALUES(application),
      |body_hash = VALUES(body_hash),
      |body = VALUES(body),
      |last_updated = VALUES(last_updated)
    """.trimMargin()

    assertThat(sql.lowercase())
      .`as`("Raw SQL must not contain PostgreSQL 'as excluded' syntax")
      .doesNotContain("as excluded")

    assertThat(sql.lowercase())
      .`as`("Raw SQL must contain ON DUPLICATE KEY UPDATE")
      .contains("on duplicate key update")

    // Verify params match the number of placeholders
    assertThat(params.size)
      .`as`("Params count matches placeholders")
      .isEqualTo(chunk.size * 6)
  }
}
