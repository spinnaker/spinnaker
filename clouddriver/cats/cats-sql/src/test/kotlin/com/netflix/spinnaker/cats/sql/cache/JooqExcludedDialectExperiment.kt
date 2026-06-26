package com.netflix.spinnaker.cats.sql.cache

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.Test

class JooqExcludedDialectExperiment {

  @Test
  fun `DSL_excluded generates valid SQL for MySQL`() {
    val ctx = DSL.using(SQLDialect.MYSQL)
    val sql = ctx.insertInto(table("t"), field("id"), field("body"))
      .values("1", "a")
      .onDuplicateKeyUpdate()
      .set(field("body"), DSL.excluded(field("body")) as Any)
      .getSQL()

    println("MySQL: $sql")
    assertThat(sql.lowercase())
      .contains("as excluded")
      .contains("excluded.body")
  }

  @Test
  fun `DSL_excluded generates valid SQL for MariaDB`() {
    val ctx = DSL.using(SQLDialect.MARIADB)
    val sql = ctx.insertInto(table("t"), field("id"), field("body"))
      .values("1", "a")
      .onDuplicateKeyUpdate()
      .set(field("body"), DSL.excluded(field("body")) as Any)
      .getSQL()

    println("MariaDB: $sql")
    assertThat(sql.lowercase())
      .contains("on duplicate key update")
      .contains("values(body)")
  }

  @Test
  fun `DSL_excluded generates valid SQL for Postgres`() {
    val ctx = DSL.using(SQLDialect.POSTGRES)
    val sql = ctx.insertInto(table("t"), field("id"), field("body"))
      .values("1", "a")
      .onConflict(field("id"))
      .doUpdate()
      .set(field("body"), DSL.excluded(field("body")) as Any)
      .getSQL()

    println("Postgres: $sql")
    assertThat(sql.lowercase())
      .contains("on conflict")
      .contains("excluded.body")
  }
}
