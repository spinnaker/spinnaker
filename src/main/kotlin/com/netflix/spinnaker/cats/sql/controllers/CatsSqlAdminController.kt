package com.netflix.spinnaker.cats.sql.controllers

import com.netflix.spinnaker.cats.sql.cache.SqlSchemaVersion
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.DriverManager

@ConditionalOnProperty("sql.cache.enabled")
@EnableConfigurationProperties(SqlProperties::class)
@RestController
@RequestMapping("/admin/db")
class CatsSqlAdminController(private val properties: SqlProperties) {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(CatsSqlAdminController::class.java) }
  }

  @PutMapping(path = ["/truncate/{namespace}"])
  fun truncateTables(@PathVariable("namespace") truncateNamespace: String,
                     @Value("\${sql.tableNamespace:#{null}}") currentNamespace: String?): TruncateTablesResult {
    if (currentNamespace == null) {
      throw IllegalStateException("truncate can only be called when sql.tableNamespace is set")
    }

    if (!truncateNamespace.matches("""^\w+$""".toRegex())) {
      throw IllegalArgumentException("tableNamespace can only contain characters [a-z, A-Z, 0-9, _]")
    }

    if (currentNamespace.toLowerCase() == truncateNamespace.toLowerCase()) {
      throw IllegalArgumentException("truncate cannot be called for the currently active namespace")
    }

    val conn = DriverManager.getConnection(
      properties.migration.jdbcUrl,
      properties.migration.user,
      properties.migration.password
    )

    val tablesTruncated = mutableListOf<String>()
    val sql = "show tables like 'cats_v${SqlSchemaVersion.current()}_${truncateNamespace}_%'"

    conn.use { c ->
      val jooq = DSL.using(c, SQLDialect.MYSQL)
      val rs = jooq.fetch(sql).intoResultSet()

      while (rs.next()) {
        val table = rs.getString(1)
        val truncateSql = "truncate table `$table`"
        log.info("Truncating $table")

        jooq.query(truncateSql).execute()
        tablesTruncated.add(table)
      }
    }

    return TruncateTablesResult(truncatedTableCount = tablesTruncated.size, truncatedTables = tablesTruncated)
  }
}

data class TruncateTablesResult(
  val truncatedTableCount: Int,
  val truncatedTables: Collection<String>
)
