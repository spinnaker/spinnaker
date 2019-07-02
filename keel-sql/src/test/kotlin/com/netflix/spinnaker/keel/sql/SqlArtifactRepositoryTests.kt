package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initTcMysqlDatabase
import org.junit.jupiter.api.AfterAll

internal object SqlArtifactRepositoryTests : ArtifactRepositoryTests<SqlArtifactRepository>() {
  private val testDatabase = initTcMysqlDatabase()
  private val jooq = testDatabase.context

  override fun factory() = SqlArtifactRepository(jooq)

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
