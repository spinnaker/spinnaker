package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDatabase
import org.jooq.SQLDialect.MYSQL
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainerProvider

internal val testDatabase by lazy {
  initDatabase(mySQLContainer.authenticatedJdbcUrl, MYSQL, "keel")
}

internal val mySQLContainer = MySQLContainerProvider()
  .newInstance("8.0.36")
  .withDatabaseName("keel")
  .withUsername("keel_service")
  .withPassword("whatever")
  .withReuse(true)
  // Fixes test failures where the query is trying to use latin1_swedish_ci collation for some godforsaken reason
  .withCommand("mysqld --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci")
  .also { it.start() }

@Suppress("UsePropertyAccessSyntax")
private val JdbcDatabaseContainer<*>.authenticatedJdbcUrl: String
  get() = "${getJdbcUrl()}?user=${getUsername()}&password=${getPassword()}&useSSL=false"
