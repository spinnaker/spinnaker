package com.netflix.spinnaker.keel.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.configuration.ConfigurationContainer
import liquibase.configuration.GlobalConfiguration
import liquibase.configuration.LiquibaseConfiguration
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.Schema
import org.jooq.conf.RenderNameStyle.AS_IS
import org.jooq.impl.DSL.currentSchema
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.slf4j.LoggerFactory
import java.sql.SQLException

internal fun initDatabase(jdbcUrl: String, sqlDialect: SQLDialect): DSLContext {
  val dataSource = HikariDataSource(
    HikariConfig().also {
      it.jdbcUrl = jdbcUrl
      it.maximumPoolSize = 5
    }
  )

  val config = DefaultConfiguration().also {
    it.set(DataSourceConnectionProvider(dataSource))
    it.setSQLDialect(sqlDialect)
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

internal fun DSLContext.flushAll() {
  val schema = select(currentSchema())
    .fetch()
    .getValue(0, 0)
  with(LiquibaseConfiguration.getInstance().getConfiguration<GlobalConfiguration>()) {
    meta()
      .schemas
      .filter { it.name == schema }
      .flatMap(Schema::getTables)
      .filterNot {
        it.name in setOf(
          databaseChangeLogTableName,
          databaseChangeLogLockTableName
        )
      }
      .forEach {
        truncate(it).execute()
      }
  }
}

private inline fun <reified T : ConfigurationContainer> LiquibaseConfiguration.getConfiguration(): T =
  getConfiguration(T::class.java)

private val log by lazy { LoggerFactory.getLogger(::initDatabase.javaClass) }
