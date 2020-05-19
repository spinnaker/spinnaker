package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.DummyResourceSpecIdentifier
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import dev.minutest.rootContext
import io.mockk.confirmVerified
import java.time.Clock
import org.junit.jupiter.api.AfterAll
import strikt.api.expectThat
import strikt.assertions.isNull

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(5, 100)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      jooq,
      clock,
      DummyResourceSpecIdentifier,
      emptyList(),
      configuredObjectMapper(),
      sqlRetry
    )
  }

  override fun flush() {
    cleanupDb(jooq)
  }

  fun moreTests() = rootContext<Fixture<SqlResourceRepository>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    after { confirmVerified(callback) }
    after { flush() }

    context("race condition between storing a resource and retrieving its uid") {
      val resource = resource()
      test("retries and succeeds retrieving resource uid") {
        var exception: Throwable? = null
        Thread {
          Thread.sleep(200) // timeout is 5 retries * 100 millis
          subject.store(resource)
        }.start()
        Thread {
          try {
            subject.getResourceUid(resource.id)
          } catch (e: IllegalStateException) {
            exception = e
          }
        }.also {
          it.start()
          it.join()
          expectThat(exception).isNull()
        }
      }
    }
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
