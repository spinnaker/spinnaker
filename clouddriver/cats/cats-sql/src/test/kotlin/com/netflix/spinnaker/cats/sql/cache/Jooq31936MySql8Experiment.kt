package com.netflix.spinnaker.cats.sql.cache

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.Test

/**
 * Experiment: verify jOOQ 3.19.36 generates valid MySQL 8.0.19+ syntax
 * using DSL.excluded() inside onDuplicateKeyUpdate().
 */
class Jooq31936MySql8Experiment {

  private val ctx = DSL.using(SQLDialect.MYSQL)

  @Test
  fun `jOOQ 3_19_36 onDuplicateKeyUpdate with DSL_excluded generates MySQL 8 row alias syntax`() {
    val insert = ctx.insertInto(table("t"), field("id"), field("body"))
    insert.values("id-1", "body")
    insert.onDuplicateKeyUpdate()
      .set(field("body"), DSL.excluded(field("body")) as Any)

    val sql = insert.getSQL()
    println("SQL: $sql")

    assertThat(sql.lowercase())
      .`as`("jOOQ 3.19.36 should emit 'as excluded' for MySQL dialect")
      .contains("as excluded")

    assertThat(sql.lowercase())
      .`as`("Update clause should use excluded.column for MySQL 8.0.19+")
      .contains("excluded.body")

    assertThat(sql.lowercase())
      .`as`("Must NOT contain deprecated VALUES(col) which fails when mixed with row alias")
      .doesNotContain("values(body)")
  }

  @Test
  fun `jOOQ 3_19_36 multi-row onDuplicateKeyUpdate with DSL_excluded`() {
    val insert = ctx.insertInto(table("t"), field("id"), field("body"))
    insert.values("id-1", "body-1")
    insert.values("id-2", "body-2")
    insert.onDuplicateKeyUpdate()
      .set(field("body"), DSL.excluded(field("body")) as Any)

    val sql = insert.getSQL()
    println("Multi-row SQL: $sql")

    assertThat(sql.lowercase())
      .contains("as excluded")

    assertThat(sql.lowercase())
      .contains("excluded.body")
  }

  @Test
  fun `jOOQ 3_19_36 onDuplicateKeyUpdate with literal values still works`() {
    // Orca pattern: .set(Map<Field, Any>) passes literal values
    val insert = ctx.insertInto(table("t"), field("id"), field("status"))
      .values("id-1", "RUNNING")
      .onDuplicateKeyUpdate()
      .set(field("status"), "RUNNING")

    val sql = insert.getSQL()
    println("Literal values SQL: $sql")

    // MySQL 8.0.19+ accepts row alias even when update clause uses literals
    assertThat(sql.lowercase())
      .`as`("Literal values in update clause should be valid with row alias")
      .contains("on duplicate key update status = ?")
  }
}
