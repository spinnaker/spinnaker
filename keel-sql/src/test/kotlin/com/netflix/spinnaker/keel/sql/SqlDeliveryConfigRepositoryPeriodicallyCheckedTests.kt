package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.persistence.DummyResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import dev.minutest.rootContext
import org.junit.jupiter.api.AfterAll
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
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
    SqlDeliveryConfigRepository(jooq, clock, DummyResourceSpecIdentifier, objectMapper, sqlRetry, defaultArtifactSuppliers())
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
}
