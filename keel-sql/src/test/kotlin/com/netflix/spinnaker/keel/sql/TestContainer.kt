package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDatabase
import org.jooq.SQLDialect.MYSQL_5_7
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainerProvider

internal fun initTestDatabase() = initDatabase(
  mySQLContainer.authenticatedJdbcUrl,
  MYSQL_5_7
)

private val mySQLContainer = MySQLContainerProvider()
  .newInstance("5.7.22")
  .withDatabaseName("keel")
  .withUsername("keel_service")
  .withUsername("whatever")
  .also { it.start() }

@Suppress("UsePropertyAccessSyntax")
private val JdbcDatabaseContainer<*>.authenticatedJdbcUrl: String
  get() = "${getJdbcUrl()}?user=${getUsername()}&password=${getPassword()}"
