package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDatabase
import org.jooq.SQLDialect.MYSQL_5_7
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainerProvider

internal object SqlArtifactRepositoryTests : ArtifactRepositoryTests<SqlArtifactRepository>() {
  private val testContainer = MySQLContainerProvider()
    .newInstance("5.7.22")
    .withDatabaseName("keel")
    .withUsername("keel_service")
    .withUsername("whatever")
    .also { it.start() }

  private val testDatabase = initDatabase(
    testContainer.authenticatedJdbcUrl,
    MYSQL_5_7
  )

  private val jooq = testDatabase.context

  override fun factory() = SqlArtifactRepository(jooq)

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
    testContainer.stop()
  }

  @Suppress("UsePropertyAccessSyntax")
  private val JdbcDatabaseContainer<*>.authenticatedJdbcUrl: String
    get() = "${getJdbcUrl()}?user=${getUsername()}&password=${getPassword()}"
}
