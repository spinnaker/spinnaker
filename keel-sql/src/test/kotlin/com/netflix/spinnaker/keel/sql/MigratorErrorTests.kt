package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isFailure
import strikt.assertions.isSuccess
import java.time.Clock.systemUTC
import java.time.Duration.ofMinutes

/**
 * Tests that exceptions thrown by migrators are handled gracefully when reading from the database.
 */
class MigratorErrorTests {
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(5, 0)
  private val objectMapper = configuredTestObjectMapper()
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  private val kindV1 = kind<DummyResourceSpec>(parseKind("test/whatever@v1"))
  private val kindV2 = kind<DummyResourceSpec>(parseKind("test/whatever@v2"))

  private val multiVersionResourceSpecIdentifier = ResourceSpecIdentifier(kindV1, kindV2)

  private val bedShittingSpecMigrator =
    object : SpecMigrator<DummyResourceSpec, DummyResourceSpec> {
      override val input = kindV1
      override val output = kindV2

      override fun migrate(spec: DummyResourceSpec): DummyResourceSpec {
        throw RuntimeException("💩🛏")
      }
    }

  private val resourceRepository = SqlResourceRepository(
    jooq = jooq,
    clock = systemUTC(),
    resourceSpecIdentifier = multiVersionResourceSpecIdentifier,
    specMigrators = listOf(bedShittingSpecMigrator),
    objectMapper = objectMapper,
    sqlRetry = sqlRetry
  )

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq = jooq,
    clock = systemUTC(),
    resourceSpecIdentifier = multiVersionResourceSpecIdentifier,
    objectMapper = objectMapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = defaultArtifactSuppliers(),
    specMigrators = listOf(bedShittingSpecMigrator)
  )

  val deliveryConfig = DeliveryConfig(
    name = "fnord-manifest",
    application = "fnord",
    serviceAccount = "keel@spinnaker",
    environments = setOf(
      Environment(
        name = "test",
        resources = setOf(
          resource(kind = kindV1.kind),
          resource(kind = kindV2.kind)
        )
      )
    )
  )

  @BeforeEach
  fun persistData() {
    deliveryConfig.environments.first().resources.forEach {
      resourceRepository.store(it)
    }
    deliveryConfigRepository.store(deliveryConfig)
  }

  @Test
  fun `the failing delivery config does not block the environment check cycle`() {
    // The first time our un-readable delivery config will be due for a check, so the method should fail
    expectCatching {
      deliveryConfigRepository.itemsDueForCheck(ofMinutes(1), 10)
    }
      .isFailure()
      .isA<RuntimeException>()

    // Subsequently we should not try to re-check the same delivery config (within the time window)
    expectCatching {
      deliveryConfigRepository.itemsDueForCheck(ofMinutes(1), 10)
    }
      .isSuccess()
      .isEmpty()
  }

  @AfterEach
  fun flush() {
    cleanupDb(jooq)
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
