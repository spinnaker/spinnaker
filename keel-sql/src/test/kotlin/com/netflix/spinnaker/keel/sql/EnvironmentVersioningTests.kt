package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency.normal
import com.netflix.spinnaker.keel.api.NotificationType.slack
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.LATEST_ENVIRONMENT
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.randomString
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.time.Clock
import java.time.Duration

class EnvironmentVersioningTests {
  private val jooq = testDatabase.context
  private val objectMapper = configuredTestObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val resourceSpecIdentifier =
    ResourceSpecIdentifier(
      kind<DummyResourceSpec>("test/whatever@v1")
    )
  val deliveryConfig = deliveryConfig()

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq,
    Clock.systemUTC(),
    resourceSpecIdentifier,
    objectMapper,
    sqlRetry,
    defaultArtifactSuppliers()
  )

  private val resourceRepository = SqlResourceRepository(
    jooq,
    Clock.systemUTC(),
    resourceSpecIdentifier,
    emptyList(),
    objectMapper,
    sqlRetry
  )

  @BeforeEach
  fun storeDeliveryConfig() {
    deliveryConfig.resources.forEach(resourceRepository::store)
    deliveryConfigRepository.store(deliveryConfig)
  }

  @AfterEach
  fun flush() {
    cleanupDb(jooq)
  }

  @Test
  fun `storing an environment with no changes does not create a new version`() {
    val initialVersion = latestVersion()
    expectThat(initialVersion).describedAs("initial version") isEqualTo 1

    deliveryConfigRepository.store(deliveryConfig)

    val updatedVersion = latestVersion()
    expectThat(updatedVersion).describedAs("updated version") isEqualTo initialVersion
  }

  @Test
  fun `storing an environment with updated constraints, verifications, or notifications does not create a new version`() {
    val initialVersion = latestVersion()
    expectThat(initialVersion).describedAs("initial version") isEqualTo 1

    deliveryConfig.run {
      copy(
        environments = environments.first().run {
          copy(
            constraints = setOf(ManualJudgementConstraint()),
            verifyWith = listOf(DummyVerification()),
            notifications = setOf(NotificationConfig(type = slack,
              address = "#trashpandas",
              frequency = normal))
          )
        }
          .let(::setOf)
      )
    }
    deliveryConfigRepository.store(deliveryConfig)

    val updatedVersion = latestVersion()
    expectThat(updatedVersion).describedAs("updated version") isEqualTo initialVersion
  }

  @Test
  fun `storing an environment with an updated resource creates a new version`() {
    val initialVersion = latestVersion()
    expectThat(initialVersion).describedAs("initial version") isEqualTo 1

    deliveryConfig.withUpdatedResource()
      .also(deliveryConfigRepository::store)

    val updatedVersion = latestVersion()
    expectThat(updatedVersion).describedAs("updated version") isEqualTo initialVersion + 1
  }

  @Test
  fun `after creating a new environment version it's still possible to get the environment for a resource`() {
    deliveryConfig.withUpdatedResource()
      .also(deliveryConfigRepository::store)

    expectCatching {
      deliveryConfigRepository.environmentFor(deliveryConfig.resources.first().id)
    }
      .isSuccess()
      .and {
        get(Environment::name) isEqualTo deliveryConfig.environments.first().name
        get(Environment::resources) hasSize deliveryConfig.environments.first().resources.size
      }
  }

  @Test
  fun `after creating a new environment version it's still possible to get the delivery config for a resource`() {
    deliveryConfig.withUpdatedResource()
      .also(deliveryConfigRepository::store)

    expectCatching {
      deliveryConfigRepository.deliveryConfigFor(deliveryConfig.resources.first().id)
    }
      .isSuccess()
      .get(DeliveryConfig::name) isEqualTo deliveryConfig.name
  }

  @Test
  fun `after creating a new environment version only the newest resource version gets checked`() {
    deliveryConfig.withUpdatedResource()
      .also(deliveryConfigRepository::store)

    expectCatching {
      resourceRepository.itemsDueForCheck(Duration.ofMinutes(1), 1)
    }
      .isSuccess()
      .hasSize(1)
      .first()
      .get { metadata["version"] } isEqualTo 2

    expectCatching {
      resourceRepository.itemsDueForCheck(Duration.ofMinutes(1), 1)
    }
      .isSuccess()
      .isEmpty()
  }

  @Test
  fun `after creating a new environment version only the newest environment version gets checked`() {
    deliveryConfig.withUpdatedResource()
      .also(deliveryConfigRepository::store)

    expectCatching {
      deliveryConfigRepository.itemsDueForCheck(Duration.ofMinutes(1), 1)
    }
      .isSuccess()
      .hasSize(1)
      .first()
      .get(DeliveryConfig::environments)
      .hasSize(1)
      .first()
      .get(Environment::resources)
      .hasSize(1)
      .first()
      .get { metadata["version"] } isEqualTo 2

    expectCatching {
      deliveryConfigRepository.itemsDueForCheck(Duration.ofMinutes(1), 1)
    }
      .isSuccess()
      .isEmpty()
  }

  private fun DeliveryConfig.withUpdatedResource() =
    copy(
      environments = environments.first().run {
        copy(resources = resources.first().run {
          copy(spec = (spec as DummyResourceSpec).run {
            copy(data = randomString())
          })
        }
          .also(resourceRepository::store)
          .let(::setOf)
        )
      }
        .let(::setOf)
    )

  private fun latestVersion() =
    jooq
      .select(LATEST_ENVIRONMENT.VERSION)
      .from(LATEST_ENVIRONMENT)
      .fetchOne(LATEST_ENVIRONMENT.VERSION)

  data class DummyVerification(
    override val id: String = "whatever",
  ) : Verification {
    override val type = "verification"
  }
}
