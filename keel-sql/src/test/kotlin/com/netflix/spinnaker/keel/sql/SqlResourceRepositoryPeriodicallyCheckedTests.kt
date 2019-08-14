package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import dev.minutest.rootContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Clock

internal object SqlResourceRepositoryPeriodicallyCheckedTests :
  ResourceRepositoryPeriodicallyCheckedTests<SqlResourceRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context

  override val factory: (clock: Clock) -> SqlResourceRepository = { clock ->
    SqlResourceRepository(jooq, clock, configuredObjectMapper())
  }

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }

  fun parallelCheckingTests() = rootContext<Fixture<ResourceHeader, SqlResourceRepository>> {
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
          val resource = resource(name = { "fnord-$i" })
          subject.store(resource)
        }
      }

      test("each thread gets a unique set of resources") {
        val results = mutableSetOf<ResourceHeader>()
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
          .select(field<String>("metadata"))
          .from("resource")
          .fetchOne()
          .let { (metadata) ->
            configuredObjectMapper().readValue<Map<String, Any?>>(metadata)
          }
          .also { metadata ->
            expectThat(metadata)
              .containsKey("uid")
              .containsKey("name")
          }
      }

      test("uid is stored consistently") {
        jooq
          .select(field<String>("uid"), field<String>("metadata"))
          .from("resource")
          .fetchOne()
          .also { (uid, metadata) ->
            val metadataMap = configuredObjectMapper().readValue<Map<String, Any?>>(metadata)
            expectThat(uid)
              .isEqualTo(metadataMap["uid"].toString())
          }
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
