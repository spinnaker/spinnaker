package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import org.jooq.SQLDialect.MYSQL_5_7
import org.junit.jupiter.api.AfterAll

internal object SqlArtifactRepositoryTests : ArtifactRepositoryTests<SqlArtifactRepository>() {
  private val jooq = initDatabase(
    "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    MYSQL_5_7
  )

  override fun factory() = SqlArtifactRepository(jooq)

  override fun flush() {
    jooq.flushAll()
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
  }
}
