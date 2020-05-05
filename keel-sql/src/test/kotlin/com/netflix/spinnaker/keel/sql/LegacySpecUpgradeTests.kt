package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock.systemUTC
import java.time.Instant
import java.time.Instant.EPOCH
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

internal class LegacySpecUpgradeTests : JUnit5Minutests {

  data class SpecV1(
    val name: String
  ) : ResourceSpec {
    override val id = name
    override val application = "fnord"
  }

  data class SpecV2(
    val name: String,
    val number: Int
  ) : ResourceSpec {
    override val id = name
    override val application = "fnord"
  }

  data class SpecV3(
    val name: String,
    val number: Int,
    val timestamp: Instant
  ) : ResourceSpec {
    override val id = name
    override val application = "fnord"
  }

  object Fixture {
    @JvmStatic
    val testDatabase = initTestDatabase()

    private val jooq = testDatabase.context
    private val retryProperties = RetryProperties(1, 0)
    private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

    val v1 = SupportedKind(ResourceKind.parseKind("test/whatever@v1"), SpecV1::class.java)
    val v2 = SupportedKind(ResourceKind.parseKind("test/whatever@v2"), SpecV2::class.java)
    val v3 = SupportedKind(ResourceKind.parseKind("test/whatever@v3"), SpecV3::class.java)

    val resourceTypeIdentifier = ResourceSpecIdentifier(v1, v2, v3)

    val v1to2Migrator = object : SpecMigrator<SpecV1, SpecV2> {
      override val input = v1
      override val output = v2

      override fun migrate(spec: SpecV1): SpecV2 =
        SpecV2(spec.name, 1)
    }
    val v2to3Migrator = object : SpecMigrator<SpecV2, SpecV3> {
      override val input = v2
      override val output = v3

      override fun migrate(spec: SpecV2): SpecV3 =
        SpecV3(spec.name, spec.number, EPOCH)
    }

    val repository = SqlResourceRepository(
      jooq,
      systemUTC(),
      resourceTypeIdentifier,
      listOf(v1to2Migrator, v2to3Migrator),
      configuredObjectMapper(),
      sqlRetry
    )

    val ancientResource = resource(v1.kind, SpecV1("whatever"))
    val oldResource = resource(v2.kind, SpecV2("whatever", 2))

    fun flush() {
      cleanupDb(jooq)
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    after {
      flush()
    }

    context("a spec with the old kind exists in the database") {
      before {
        repository.store(oldResource)
      }

      context("retrieving the resource") {
        test("the spec is converted to the new kind before being returned") {
          expectCatching { repository.get(oldResource.id) }
            .isSuccess()
            .isA<Resource<SpecV3>>()
            .and {
              get { spec.name }.isEqualTo(oldResource.spec.name)
              get { spec.number }.isEqualTo(oldResource.spec.number)
              get { spec.timestamp }.isEqualTo(EPOCH)
            }
        }
      }
    }

    context("a spec with an even older kind exists in the database") {
      before {
        repository.store(ancientResource)
      }

      context("retrieving the resource") {
        test("the spec is converted to the newest kind before being returned") {
          expectCatching { repository.get(oldResource.id) }
            .isSuccess()
            .isA<Resource<SpecV3>>()
            .and {
              get { spec.name }.isEqualTo(ancientResource.spec.name)
              get { spec.number }.isEqualTo(1)
              get { spec.timestamp }.isEqualTo(EPOCH)
            }
        }
      }
    }

    afterAll {
      Fixture.testDatabase.dataSource.close()
    }
  }
}
