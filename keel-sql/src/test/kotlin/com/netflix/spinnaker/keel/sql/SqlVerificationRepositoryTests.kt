package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.jackson.registerKeelApiModule
import com.netflix.spinnaker.keel.persistence.VerificationRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach

internal class SqlVerificationRepositoryTests :
  VerificationRepositoryTests<SqlVerificationRepository>() {

  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val artifactSuppliers = listOf(DockerArtifactSupplier(mockk(), mockk(), mockk()))

  private val mapper = configuredObjectMapper()
    .registerKeelApiModule()
    .apply {
      registerSubtypes(NamedType(DummyVerification::class.java, "dummy"))
    }

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq = jooq,
    clock = clock,
    resourceSpecIdentifier = ResourceSpecIdentifier(),
    objectMapper = mapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = artifactSuppliers
  )
  private val artifactRepository = SqlArtifactRepository(
    jooq = jooq,
    clock = clock,
    objectMapper = mapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = artifactSuppliers
  )

  private val pausedRepository = SqlPausedRepository(
    jooq = jooq,
    sqlRetry = sqlRetry,
    clock = clock
  )

  override fun createSubject() =
    SqlVerificationRepository(
      jooq = jooq,
      clock = clock,
      resourceSpecIdentifier = mockk(),
      objectMapper = mapper,
      sqlRetry = sqlRetry,
      artifactSuppliers = artifactSuppliers
    )

  override fun VerificationContext.setup() {
    deliveryConfig.artifacts.forEach(artifactRepository::register)
    deliveryConfigRepository.store(deliveryConfig)
    artifactRepository.storeArtifactVersion(
      PublishedArtifact(
        artifact.name,
        artifact.type,
        version
      )
    )
  }

  override fun VerificationContext.setupCurrentArtifactVersion() {
    artifactRepository.markAsSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      environmentName
    )
  }

  override fun VerificationContext.pauseApplication() {
    pausedRepository.pauseApplication(
      deliveryConfig.application,
      "fzlem@netflix.com"
    )
  }

  @AfterEach
  fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  companion object {
    private val testDatabase = initTestDatabase()

    @JvmStatic
    @AfterAll
    fun shutdown() {
      testDatabase.dataSource.close()
    }
  }
}
