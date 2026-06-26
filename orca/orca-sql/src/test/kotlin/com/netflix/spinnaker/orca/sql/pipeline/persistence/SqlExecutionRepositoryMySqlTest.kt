package com.netflix.spinnaker.orca.sql.pipeline.persistence

import org.assertj.core.api.Assertions.assertThat
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test

/**
 * Regression tests for jOOQ 3.19+ MySQL `ON DUPLICATE KEY UPDATE` rendering bug.
 *
 * jOOQ 3.19 (via Spring Boot 3.5.x) generates invalid MySQL syntax when using
 * [InsertValuesStep.onDuplicateKeyUpdate] — it emits PostgreSQL-specific
 * `AS excluded` even when the configured dialect is MySQL.
 */
class SqlExecutionRepositoryMySqlTest {

  @Test
  fun `jOOQ 3_19 onDuplicateKeyUpdate generates invalid as excluded for MySQL dialect`() {
    val ctx = DSL.using(org.jooq.SQLDialect.MYSQL)
    val insert = ctx.insertInto(
      DSL.table("pipelines"),
      DSL.field("id"),
      DSL.field("body"),
      DSL.field("status")
    )

    insert.values("id-1", "body", "status")
    insert.onDuplicateKeyUpdate()
      .set(DSL.field("body"), DSL.field("body") as Any)
      .set(DSL.field("status"), DSL.field("status") as Any)

    val sql = insert.getSQL()

    assertThat(sql.lowercase())
      .`as`("jOOQ 3.19 generates 'as excluded' even for MySQL dialect")
      .contains("as excluded")
  }

  @Test
  fun `raw SQL upsert does not contain as excluded`() {
    val tableName = "pipelines"
    val columns = "id, body, status"
    val placeholders = "(?, ?, ?)"
    val updateClause = "body = VALUES(body), status = VALUES(status)"

    val sql = """
      |INSERT INTO $tableName ($columns)
      |VALUES $placeholders
      |ON DUPLICATE KEY UPDATE
      |$updateClause
    """.trimMargin()

    assertThat(sql.lowercase())
      .`as`("Raw SQL must not contain PostgreSQL 'as excluded' syntax")
      .doesNotContain("as excluded")

    assertThat(sql.lowercase())
      .`as`("Raw SQL must contain ON DUPLICATE KEY UPDATE")
      .contains("on duplicate key update")
  }
}
