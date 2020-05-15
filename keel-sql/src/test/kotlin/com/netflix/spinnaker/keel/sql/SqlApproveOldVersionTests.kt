package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.persistence.ApproveOldVersionTests
import com.netflix.spinnaker.keel.persistence.CombinedRepository
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.mockk.mockk
import java.time.Clock

class SqlApproveOldVersionTests : ApproveOldVersionTests<CombinedRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val clock = Clock.systemUTC()

  override fun createKeelRepository(resourceSpecIdentifier: ResourceSpecIdentifier, mapper: ObjectMapper): CombinedRepository {
    val deliveryConfigRepository = SqlDeliveryConfigRepository(jooq, clock, resourceSpecIdentifier, mapper, sqlRetry)
    val resourceRepository = SqlResourceRepository(jooq, clock, resourceSpecIdentifier, emptyList(), mapper, sqlRetry)
    val artifactRepository = SqlArtifactRepository(jooq, clock, mapper, sqlRetry)
    return CombinedRepository(
      deliveryConfigRepository,
      artifactRepository,
      resourceRepository,
      clock,
      mockk(relaxed = true)
    )
  }

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }
}
