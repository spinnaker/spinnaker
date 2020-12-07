package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.persistence.DummyResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import dev.minutest.rootContext
import org.junit.jupiter.api.AfterAll
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration

internal object SqlDeliveryConfigRepositoryPeriodicallyCheckedTests :
  DeliveryConfigRepositoryPeriodicallyCheckedTests<SqlDeliveryConfigRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val objectMapper = configuredTestObjectMapper()
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override val factory: (Clock) -> SqlDeliveryConfigRepository = { clock ->
    SqlDeliveryConfigRepository(
      jooq = jooq,
      clock = clock,
      resourceSpecIdentifier = DummyResourceSpecIdentifier,
      objectMapper = objectMapper,
      sqlRetry = sqlRetry,
      artifactSuppliers = defaultArtifactSuppliers()
    )
  }

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }

  fun pausedApplicationTests() = rootContext<Fixture<DeliveryConfig, SqlDeliveryConfigRepository>> {
    fixture {
      Fixture(factory, createAndStore, updateOne)
    }

    after { flush() }

    context("an application is paused") {
      before {
        createAndStore(2)
          .also {
            SqlPausedRepository(jooq, sqlRetry, clock)
              .pauseApplication(it.first().application, "fzlem@netflix.com")
          }
      }

      test("delivery config is ignored") {
        expectThat(nextResults()).hasSize(1)
      }
    }
  }

  fun lockExpiryTests() = rootContext<Fixture<DeliveryConfig, SqlDeliveryConfigRepository>> {
    fixture {
      Fixture(factory, createAndStore, updateOne)
    }

    after { flush() }

    context("a delivery config was locked for checking previously") {
      before {
        createAndStore(1)
        nextResults()
      }

      test("the delivery config is not returned in a check cycle") {
        expectThat(nextResults()).isEmpty()
      }

      context("after some time passes") {
        before {
          clock.incrementBy(Duration.ofMinutes(35))
        }

        test("the lock expires") {
          expectThat(nextResults()).hasSize(1)
        }
      }
    }
  }

  fun resourceMigrationTests() = rootContext<Fixture<DeliveryConfig, SqlDeliveryConfigRepository>> {

    val v1 = kind<DummyResourceSpec>("test/whatever@v1")
    val v2 = kind<DummyResourceSpec>("test/whatever@v2")

    val multipleVersionsResourceSpecIdentifier = ResourceSpecIdentifier(v1, v2)

    val migrator = object : SpecMigrator<DummyResourceSpec, DummyResourceSpec> {
      override val input = v1
      override val output = v2

      override fun migrate(spec: DummyResourceSpec): DummyResourceSpec = spec
    }

    fixture {
      val resourceRepository = SqlResourceRepository(
        jooq = jooq,
        clock = Clock.systemDefaultZone(),
        resourceSpecIdentifier = multipleVersionsResourceSpecIdentifier,
        specMigrators = listOf(migrator),
        objectMapper = objectMapper,
        sqlRetry = sqlRetry
      )

      val factory = { clock: Clock ->
        SqlDeliveryConfigRepository(
          jooq = jooq,
          clock = clock,
          resourceSpecIdentifier = multipleVersionsResourceSpecIdentifier,
          objectMapper = objectMapper,
          sqlRetry = sqlRetry,
          artifactSuppliers = defaultArtifactSuppliers(),
          specMigrators = listOf(migrator)
        )
      }

      val createAndStore: Fixture<DeliveryConfig, SqlDeliveryConfigRepository>.(count: Int) -> Collection<DeliveryConfig> = { count ->
        (1..count)
          .map { i ->
            val resource = DummyResourceSpec(
              application = "fnord-$i"
            ).let { spec ->
              Resource(
                kind = v1.kind,
                metadata = mapOf(
                  "id" to spec.id,
                  "application" to spec.application
                ),
                spec = spec
              )
            }

            resourceRepository.store(resource)

            DeliveryConfig(
              name = "delivery-config-$i",
              application = "fnord-$i",
              serviceAccount = "keel@spinnaker",
              environments = setOf(
                Environment(
                  name = "first",
                  resources = setOf(resource)
                )
              )
            )
              .also(subject::store)
          }
      }

      Fixture(factory, createAndStore, updateOne)
    }

    after { flush() }

    context("a resource with an obsolete version belongs to a delivery config") {
      before {
        createAndStore(1)
      }

      test("the resources in the delivery config are auto-migrated to the latest version") {
        expectThat(nextResults())
          .first()
          .get { environments.first().resources.first().kind }
          .isEqualTo(v2.kind)
      }
    }
  }
}
