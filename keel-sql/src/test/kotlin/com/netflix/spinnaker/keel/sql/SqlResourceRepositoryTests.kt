package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.persistence.randomData
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
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

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      jooq,
      clock,
      configuredObjectMapper()
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

  fun parallelCheckingTests() = rootContext<CheckLockFixture<SqlResourceRepository>> {
    fixture {
      CheckLockFixture(
        subject = factory(Clock.systemDefaultZone())
      )
    }

    after { flush() }

    /**
     * I'd like to have this test in the superclass but the in-memory implementation is not designed
     * to be thread safe.
     */
    context("many threads are checking simultaneously") {
      before {
        repeat(1000) {
          val resource = Resource(
            apiVersion = SPINNAKER_API_V1,
            metadata = mapOf(
              "name" to "ec2:security-group:test:us-west-2:fnord-$it",
              "uid" to randomUID(),
              "serviceAccount" to "keel@spinnaker"
            ) + randomData(),
            kind = "security-group",
            spec = randomData()
          )
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
        val resource = Resource(
          apiVersion = SPINNAKER_API_V1,
          metadata = mapOf(
            "name" to "ec2:security-group:test:us-west-2:fnord",
            "uid" to randomUID(),
            "serviceAccount" to "keel@spinnaker"
          ) + randomData(),
          kind = "security-group",
          spec = randomData()
        )
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
