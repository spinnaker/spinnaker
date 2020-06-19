package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactPublishers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import java.time.Clock

class SqlArtifactRepositoryPeriodicallyCheckedTests :
  ArtifactRepositoryPeriodicallyCheckedTests<SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectMapper = configuredObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override val factory: (clock: Clock) -> SqlArtifactRepository = { clock ->
    SqlArtifactRepository(jooq, clock, objectMapper, sqlRetry, defaultArtifactPublishers())
  }
}
