/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model

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
