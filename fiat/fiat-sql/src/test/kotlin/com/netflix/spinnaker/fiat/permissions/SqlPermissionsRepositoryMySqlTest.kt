package com.netflix.spinnaker.fiat.permissions

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test

/**
 * Regression tests for jOOQ 3.19+ MySQL `ON DUPLICATE KEY UPDATE` rendering bug.
 *
 * jOOQ 3.19 (via Spring Boot 3.5.x) generates invalid MySQL syntax when using
 * [InsertValuesStep.onDuplicateKeyUpdate] — it emits PostgreSQL-specific
 * `AS excluded` even when the configured dialect is MySQL.
 */
class SqlPermissionsRepositoryMySqlTest {

  @Test
  fun `jOOQ 3_19 onDuplicateKeyUpdate generates invalid as excluded for MySQL dialect`() {
    val ctx = DSL.using(SQLDialect.MYSQL)
    val insert = ctx.insertInto(
      DSL.table("user"),
      DSL.field("id"),
      DSL.field("admin"),
      DSL.field("account_manager"),
      DSL.field("updated_at")
    )

    insert.values("test", true, false, 1L)
    insert.onDuplicateKeyUpdate()
      .set(DSL.field("admin"), DSL.field("admin") as Any)
      .set(DSL.field("account_manager"), DSL.field("account_manager") as Any)
      .set(DSL.field("updated_at"), DSL.field("updated_at") as Any)

    val sql = insert.getSQL(ParamType.INLINED)

    assertThat(sql.lowercase())
      .`as`("jOOQ 3.19 generates 'as excluded' even for MySQL dialect")
      .contains("as excluded")
  }

  @Test
  fun `raw SQL upsert for user does not contain as excluded`() {
    val sql = """
      |INSERT INTO user (id, admin, account_manager, updated_at)
      |VALUES (?, ?, ?, ?)
      |ON DUPLICATE KEY UPDATE
      |admin = VALUES(admin),
      |account_manager = VALUES(account_manager),
      |updated_at = VALUES(updated_at)
    """.trimMargin()

    assertThat(sql.lowercase())
      .`as`("Raw SQL must not contain PostgreSQL 'as excluded' syntax")
      .doesNotContain("as excluded")

    assertThat(sql.lowercase())
      .`as`("Raw SQL must contain ON DUPLICATE KEY UPDATE")
      .contains("on duplicate key update")
  }

  @Test
  fun `raw SQL upsert for resource batch does not contain as excluded`() {
    val chunk = listOf("res1", "res2")
    val placeholders = chunk.joinToString(", ") { "(?, ?, ?, ?, ?)" }
    val sql = """
      |INSERT INTO resource (resource_type, resource_name, body, body_hash, updated_at)
      |VALUES $placeholders
      |ON DUPLICATE KEY UPDATE
      |body = VALUES(body),
      |body_hash = VALUES(body_hash),
      |updated_at = VALUES(updated_at)
    """.trimMargin()

    assertThat(sql.lowercase())
      .`as`("Raw SQL must not contain PostgreSQL 'as excluded' syntax")
      .doesNotContain("as excluded")

    assertThat(sql.lowercase())
      .`as`("Raw SQL must contain ON DUPLICATE KEY UPDATE")
      .contains("on duplicate key update")
  }
}
