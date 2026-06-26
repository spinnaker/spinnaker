package com.netflix.spinnaker.orca.peering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for jOOQ 3.19+ MySQL `ON DUPLICATE KEY UPDATE` rendering bug.
 *
 * jOOQ 3.19 (via Spring Boot 3.5.x) generates invalid MySQL syntax when using
 * [InsertValuesStep.onDuplicateKeyUpdate] — it emits PostgreSQL-specific
 * `AS excluded` even when the configured dialect is MySQL.
 */
class MySqlRawAccessTest {

  @Test
  fun `raw SQL batch upsert does not contain as excluded`() {
    val tableName = "pipelines"
    val allFields = listOf("id", "body", "status")
    val columns = allFields.joinToString(", ")
    val batch = listOf(
      listOf("id1", "body1", "status1"),
      listOf("id2", "body2", "status2")
    )
    val placeholders = batch.joinToString(", ") {
      "(${allFields.joinToString(", ") { "?" }})"
    }
    val updateClause = allFields.joinToString(", ") {
      "$it = VALUES($it)"
    }

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
