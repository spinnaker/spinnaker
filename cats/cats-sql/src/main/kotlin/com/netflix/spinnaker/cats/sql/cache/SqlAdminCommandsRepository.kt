/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spinnaker.kork.sql.config.SqlProperties
import java.sql.Connection
import java.sql.DriverManager
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class SqlAdminCommandsRepository(private val properties: SqlProperties) {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(SqlAdminCommandsRepository::class.java) }
  }

  /**
   * Truncates CATS tables of the given namespace for the current schema version.
   *
   * @param truncateNamespace namespace of tables to truncate.
   * @return String collection of the tables truncated.
   */
  fun truncateTablesByNamespace(truncateNamespace: String): Collection<String> {
    val conn = getConnection()
    val tablesTruncated = mutableListOf<String>()

    conn.use { c ->
      val jooq = DSL.using(c, SQLDialect.MYSQL)
      val rs =
        jooq.fetch(
          "show tables like ?",
          "cats_v${SqlSchemaVersion.current()}_${truncateNamespace}_%"
        ).intoResultSet()

      while (rs.next()) {
        val table = rs.getString(1)
        val truncateSql = "truncate table `$table`"
        log.info("Truncating $table")

        jooq.query(truncateSql).execute()
        tablesTruncated.add(table)
      }
    }

    return tablesTruncated
  }

  /**
   * Drops CATS tables of the given namespace for the current schema version.
   *
   * @param dropNamespace namespace of tables to drop.
   * @return String collection of the tables dropped.
   */
  fun dropTablesByNamespace(dropNamespace: String): Collection<String> {
    val conn = getConnection()
    val tablesDropped = mutableListOf<String>()

    conn.use { c ->
      val jooq = DSL.using(c, SQLDialect.MYSQL)
      val rs =
        jooq.fetch("show tables like ?", "cats_v${SqlSchemaVersion.current()}_${dropNamespace}_%")
          .intoResultSet()

      while (rs.next()) {
        val table = rs.getString(1)
        val dropSql = "drop table `$table`"
        log.info("Dropping $table")

        jooq.query(dropSql).execute()
        tablesDropped.add(table)
      }
    }

    return tablesDropped
  }

  /**
   * Drops CATS tables of the given schema version.
   *
   * @param dropVersion the schema version of tables to drop.
   * @return String collection of the tables dropped.
   */
  fun dropTablesByVersion(dropVersion: SqlSchemaVersion): Collection<String> {
    val conn = getConnection()
    val tablesDropped = mutableListOf<String>()

    conn.use { c ->
      val jooq = DSL.using(c, SQLDialect.MYSQL)
      val rs = jooq.fetch("show tables like ?", "cats_v${dropVersion.version}_%").intoResultSet()

      while (rs.next()) {
        val table = rs.getString(1)
        val dropSql = "drop table `$table`"
        log.info("Dropping $table")

        jooq.query(dropSql).execute()
        tablesDropped.add(table)
      }
    }

    return tablesDropped
  }

  private fun getConnection(): Connection {
    return DriverManager.getConnection(
      properties.migration.jdbcUrl,
      properties.migration.user,
      properties.migration.password
    )
  }
}
