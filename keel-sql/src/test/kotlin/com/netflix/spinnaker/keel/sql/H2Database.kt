package com.netflix.spinnaker.keel.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import org.h2.engine.Mode
import org.h2.engine.Mode.ModeEnum.MySQL
import org.jooq.DSLContext
import org.jooq.SQLDialect.H2
import org.jooq.Schema
import org.jooq.conf.RenderNameStyle.AS_IS
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.sql.SQLException

internal fun initDatabase(jdbcUrl: String): DSLContext {
  // Initialize the MySQL compatibility mode in H2 to _not_ insert default values to not null
  // columns. This is what MySQL does on multi-row inserts but not single row inserts.
  Mode.getInstance(MySQL.name).convertInsertNullToZero = false

  val dataSource = HikariDataSource(
    HikariConfig().also {
      it.jdbcUrl = jdbcUrl
      it.maximumPoolSize = 5
    }
  )

  val config = DefaultConfiguration().also {
    it.set(DataSourceConnectionProvider(dataSource))
    it.setSQLDialect(H2)
    it.settings().withRenderNameStyle(AS_IS)
  }

  try {
    Liquibase(
      "db/changelog-master.yml",
      ClassLoaderResourceAccessor(),
      DatabaseFactory
        .getInstance()
        .findCorrectDatabaseImplementation(JdbcConnection(dataSource.connection))
    )
      .update("test")
  } catch (e: DatabaseException) {
    log.error("Caught exception running liquibase: {}", e.message)
    throw DatabaseInitializationFailed(e)
  } catch (e: SQLException) {
    log.error("Caught exception running liquibase: {}", e.message)
    throw DatabaseInitializationFailed(e)
  } catch (e: LiquibaseException) {
    log.error("Caught exception running liquibase: {}", e.message)
    throw DatabaseInitializationFailed(e)
  }

  return DefaultDSLContext(config)
}

internal class DatabaseInitializationFailed(cause: Throwable) : RuntimeException(cause)

internal fun DSLContext.flushAll(schema: String = "PUBLIC") =
  meta()
    .schemas
    .filter { it.name == schema }
    .flatMap(Schema::getTables)
    .forEach {
      truncate(it).execute()
    }

/**
 * Force in-memory database to shutdown so if further tests re-initialize it Liquibase won't shit
 * the bed.
 */
internal fun shutdown(jdbcUrl: String) {
  DriverManager.getConnection(jdbcUrl).use {
    it.createStatement().execute("SHUTDOWN")
  }
}

private val log by lazy { LoggerFactory.getLogger(::initDatabase.javaClass) }
