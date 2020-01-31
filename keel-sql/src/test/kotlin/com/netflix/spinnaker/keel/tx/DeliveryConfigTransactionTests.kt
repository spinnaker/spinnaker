package com.netflix.spinnaker.keel.tx

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.test.DummyResourceHandler
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.ninjasquad.springmockk.SpykBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.every
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isFalse

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, TestConfiguration::class],
  webEnvironment = MOCK,
  properties = [
    "sql.enabled=true",
    "sql.connection-pools.default.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "sql.migration.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "eureka.default-to-up=false"
  ]
)
internal class DeliveryConfigTransactionTests : JUnit5Minutests {

  @SpykBean
  lateinit var artifactRepository: ArtifactRepository

  @SpykBean
  lateinit var resourceRepository: ResourceRepository

  @SpykBean
  lateinit var deliveryConfigRepository: DeliveryConfigRepository

  @Autowired
  lateinit var resourcePersister: ResourcePersister

  @Autowired
  lateinit var jooq: DSLContext

  private fun ResourceRepository.allResourceNames(): List<String> =
    mutableListOf<String>()
      .also { list ->
        allResources { list.add(it.id) }
      }

  object Fixture {
    val submittedManifest = SubmittedDeliveryConfig(
      name = "keel-manifest",
      application = "keel",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(DebianArtifact(name = "keel", deliveryConfigName = "keel-manifest")),
      environments = setOf(
        SubmittedEnvironment(
          name = "test",
          resources = setOf(SubmittedResource(
            apiVersion = "test.$SPINNAKER_API_V1",
            kind = "whatever",
            metadata = mapOf("serviceAccount" to "keel@spinnaker"),
            spec = DummyResourceSpec("test", "resource in test", "keel")
          ))
        ),
        SubmittedEnvironment(
          name = "prod",
          resources = setOf(SubmittedResource(
            apiVersion = "test.$SPINNAKER_API_V1",
            kind = "whatever",
            metadata = mapOf("serviceAccount" to "keel@spinnaker"),
            spec = DummyResourceSpec("prod", "resource in prod", "keel")
          ))
        )
      )
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    after {
      cleanupDb(jooq)
      clearAllMocks()
    }

    context("a resource attached to the delivery config fails to persist") {
      before {
        every {
          resourceRepository.store(match {
            it.id == "test:whatever:prod"
          })
        } throws DataAccessException("o noes")

        expectCatching { resourcePersister.upsert(submittedManifest) }
          .failed()
          .isA<DataAccessException>()
      }

      test("the delivery config is not persisted") {
        expectCatching { deliveryConfigRepository.get("keel-manifest") }
          .failed()
          .isA<NoSuchDeliveryConfigName>()
      }

      test("the other resources are not persisted") {
        expectThat(resourceRepository.allResourceNames())
          .isEmpty()
      }

      test("the artifact not persisted") {
        expectThat(artifactRepository.isRegistered("keel", DEB))
          .isFalse()
      }
    }

    context("an artifact attached to the delivery config fails to persist") {
      before {
        every { artifactRepository.register(any()) } throws DataAccessException("o noes")

        expectCatching { resourcePersister.upsert(submittedManifest) }
          .failed()
          .isA<DataAccessException>()
      }

      test("the delivery config is not persisted") {
        expectCatching { deliveryConfigRepository.get("keel-manifest") }
          .failed()
          .isA<NoSuchDeliveryConfigName>()
      }

      test("the resources are not persisted") {
        expectThat(resourceRepository.allResourceNames())
          .isEmpty()
      }
    }

    context("the delivery config itself fails to persist") {
      before {
        every { deliveryConfigRepository.store(any()) } throws DataAccessException("o noes")

        expectCatching { resourcePersister.upsert(submittedManifest) }
          .failed()
          .isA<DataAccessException>()
      }

      test("the resources are not persisted") {
        expectThat(resourceRepository.allResourceNames())
          .isEmpty()
      }

      test("the artifact not persisted") {
        expectThat(artifactRepository.isRegistered("keel", DEB))
          .isFalse()
      }
    }
  }
}

@Configuration
internal class TestConfiguration {
  @Bean
  fun dummyResourceHandler() = DummyResourceHandler
}
