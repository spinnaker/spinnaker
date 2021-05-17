package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.jackson.registerKeelApiModule
import com.netflix.spinnaker.keel.persistence.ActionRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.springframework.core.env.Environment
import java.time.Instant

internal class SqlActionRepositoryTests :
  ActionRepositoryTests<SqlActionRepository>() {

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
    artifactSuppliers = artifactSuppliers,
    publisher = mockk(relaxed = true)
  )
  private val artifactRepository = SqlArtifactRepository(
    jooq = jooq,
    clock = clock,
    objectMapper = mapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = artifactSuppliers,
    publisher = mockk(relaxed = true)
  )

  private val pausedRepository = SqlPausedRepository(
    jooq = jooq,
    sqlRetry = sqlRetry,
    clock = clock
  )

  /**
   * Generate a mock Spring environment that returns the default value for all boolean properties
   */
  private fun mockEnvironment() : Environment {
    val defaultValue = slot<Boolean>()
    val environment: Environment = mockk()

    every {
      environment.getProperty(any(), Boolean::class.java, capture(defaultValue))
    } answers {
      defaultValue.captured
    }

    return environment
  }

  override fun createSubject() =
    SqlActionRepository(
      jooq = jooq,
      clock = clock,
      resourceSpecIdentifier = mockk(),
      objectMapper = mapper,
      sqlRetry = sqlRetry,
      artifactSuppliers = artifactSuppliers,
      environment = mockEnvironment()
    )

  override fun ArtifactInEnvironmentContext.setup() {
    deliveryConfig.artifacts.forEach(artifactRepository::register)
    deliveryConfigRepository.store(deliveryConfig)
    artifactRepository.storeArtifactVersion(
      PublishedArtifact(
        artifact.name,
        artifact.type,
        version,
        createdAt = Instant.now()
      )
    )
  }

  override fun ArtifactInEnvironmentContext.setupCurrentArtifactVersion() {
    artifactRepository.markAsSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      environmentName
    )
  }

  override fun ArtifactInEnvironmentContext.pauseApplication() {
    pausedRepository.pauseApplication(
      deliveryConfig.application,
      "fzlem@netflix.com"
    )
  }

  @AfterEach
  fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }
}
