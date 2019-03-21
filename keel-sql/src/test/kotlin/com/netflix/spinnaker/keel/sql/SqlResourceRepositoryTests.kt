package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.jooq.SQLDialect.MYSQL_5_7
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val jooq = initDatabase(
    "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    MYSQL_5_7
  )

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      jooq,
      configuredObjectMapper(),
      clock,
      object : InstanceIdSupplier {
        override fun get() = "localhost"
      }
    )
  }

  override fun flush() {
    jooq.flushAll()
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
  }
}
