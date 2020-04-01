package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.rootContext
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.failed
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

internal object SqlResourceRepositoryPeriodicallyCheckedTests :
  ResourceRepositoryPeriodicallyCheckedTests<SqlResourceRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override val factory: (clock: Clock) -> SqlResourceRepository = { clock ->
    SqlResourceRepository(jooq, clock, DummyResourceTypeIdentifier, configuredObjectMapper(), sqlRetry)
  }

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }

  fun parallelCheckingTests() = rootContext<Fixture<Resource<out ResourceSpec>, SqlResourceRepository>> {
    fixture {
      Fixture(factory, createAndStore, updateOne)
    }

    after { flush() }

    /**
     * I'd like to have this test in the superclass but the in-memory implementation is not designed
     * to be thread safe.
     */
    context("many threads are checking simultaneously") {
      before {
        repeat(1000) { i ->
          val resource = resource(id = "fnord-$i")
          subject.store(resource)
        }
      }

      test("each thread gets a unique set of resources") {
        val results = mutableSetOf<Resource<out ResourceSpec>>()
        doInParallel(500) {
          nextResults().let(results::addAll)
        }

        expectThat(results).describedAs("number of unique resources processed").hasSize(1000)
      }
    }

    context("database schema consistency") {
      before {
        val resource = resource()
        subject.store(resource)
      }

      test("metadata is persisted") {
        jooq
          .select(RESOURCE.METADATA)
          .from(RESOURCE)
          .fetchOne()
          .let { (metadata) ->
            configuredObjectMapper().readValue<Map<String, Any?>>(metadata)
          }
          .also { metadata ->
            expectThat(metadata)
              .containsKey("uid")
              .containsKey("id")
          }
      }

      test("uid is stored consistently") {
        jooq
          .select(RESOURCE.UID, RESOURCE.METADATA)
          .from(RESOURCE)
          .fetchOne()
          .also { (uid, metadata) ->
            val metadataMap = configuredObjectMapper().readValue<Map<String, Any?>>(metadata)
            expectThat(uid)
              .isEqualTo(metadataMap["uid"].toString())
          }
      }
    }
  }

  class UnreadableResourceFixture(
    val factory: (Clock) -> SqlResourceRepository
  ) {
    val clock = MutableClock()
    val subject: SqlResourceRepository = factory(clock)

    fun nextResults(): Collection<Resource<*>> =
      subject.itemsDueForCheck(Duration.ofMinutes(30), 2)
  }

  fun unreadableResourceTests() = rootContext<Fixture<Resource<out ResourceSpec>, SqlResourceRepository>> {
    fixture {
      Fixture(factory, { count ->
        listOf(
          resource(kind = TEST_API_V1.qualify("unreadable"), id = "unreadable").also(subject::store)
        ) +
          (2..count).map { i ->
            resource(id = "readable-$i").also(subject::store)
          }
      }, updateOne)
    }

    before { createAndStore(4) }

    after { flush() }

    context("there's an unreadable resource in the database") {
      test("fetching next items throws an exception the first time it is called") {
        expectCatching { nextResults() }.failed().isA<UnsupportedKind>()
      }

      test("subsequent calls will start returning valid results") {
        expectCatching { nextResults() }.failed().isA<UnsupportedKind>()
        expectCatching { nextResults() }.succeeded().hasSize(2)
        expectCatching { nextResults() }.succeeded().hasSize(0)
      }

      test("after the last check time limit has passed we never try to read the unreadable resource again") {
        expectCatching { nextResults() }.failed().isA<UnsupportedKind>()
        expectCatching { nextResults() }.succeeded().hasSize(2)
        expectCatching { nextResults() }.succeeded().hasSize(0)
        clock.incrementBy(Duration.ofMinutes(31))
        expectCatching { nextResults() }.succeeded().hasSize(2)
        expectCatching { nextResults() }.succeeded().hasSize(1)
        expectCatching { nextResults() }.succeeded().hasSize(0)
      }
    }
  }
}

private fun doInParallel(times: Int, block: () -> Unit) {
  GlobalScope.launch {
    repeat(times) {
      launch { block() }
    }
  }.apply {
    runBlocking {
      join()
    }
  }
}
