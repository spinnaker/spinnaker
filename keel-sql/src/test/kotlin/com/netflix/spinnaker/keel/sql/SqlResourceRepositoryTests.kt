package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.SQLDialect.H2
import org.jooq.Schema
import org.jooq.conf.RenderNameStyle.AS_IS
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.junit.jupiter.api.AfterAll
import java.sql.SQLException
import java.time.Clock

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val context = initDatabase("jdbc:h2:mem:keel;MODE=MYSQL")

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      context,
      configuredObjectMapper(),
      clock,
      object : InstanceIdSupplier {
        override fun get() = "localhost"
      }
    )
  }

  override fun flush() {
    context
      .meta()
      .schemas
      .filter { it.name == "PUBLIC" }
      .flatMap(Schema::getTables)
      .forEach {
        context.truncate(it).execute()
      }
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    context.close()
  }

  private fun initDatabase(jdbcUrl: String): DefaultDSLContext {
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
      throw DatabaseInitializationFailed(e)
    } catch (e: SQLException) {
      throw DatabaseInitializationFailed(e)
    } catch (e: LiquibaseException) {
      throw DatabaseInitializationFailed(e)
    }

    return DefaultDSLContext(config)
  }
}

private class DatabaseInitializationFailed(cause: Throwable) : RuntimeException(cause)
