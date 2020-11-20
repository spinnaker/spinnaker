package com.netflix.spinnaker.keel.tx

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.spring.test.DisableSpringScheduling
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, TestConfiguration::class],
  webEnvironment = MOCK,
  properties = [
    "spinnaker.baseUrl=http://spinnaker",
    "sql.enabled=true",
    "sql.connection-pools.default.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "sql.migration.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
  ]
)
@DisableSpringScheduling
internal class DeliveryConfigDeletionTests
@Autowired constructor(
  private val repository: KeelRepository,
  private val jooq: DSLContext
) {

  val deliveryConfig = DeliveryConfig(
    name = "fnord-manifest",
    application = "fnord",
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(
      DockerArtifact(
        name = "fnord",
        deliveryConfigName = "fnord-manifest",
        reference = "fnord-docker-img"
      )
    ),
    environments = setOf("test", "production").map {
      Environment(
        name = it,
        resources = setOf(
          resource()
        )
      )
    }.toSet()
  )

  @BeforeEach
  fun persistData() {
    repository.upsertDeliveryConfig(deliveryConfig)
  }

  @AfterEach
  fun clearDatabase() {
    cleanupDb(jooq)
  }

  @Test
  fun `inserting a delivery config also inserts associated resources`() {
    expectThat(
      repository.getResourcesByApplication(deliveryConfig.application)
    ).hasSize(deliveryConfig.resources.size)
  }

  @Test
  fun `deleting a delivery config removes all associated resources`() {
    repository.deleteDeliveryConfigByName(deliveryConfig.name)

    expectThat(
      repository.getResourcesByApplication(deliveryConfig.application)
    )
      .isEmpty()
  }

  @Test
  fun `deleting a delivery config removes all associated artifacts`() {
    repository.deleteDeliveryConfigByName(deliveryConfig.name)

    expectThat(
      deliveryConfig.artifacts.first().run {
        repository.getArtifact(name, type, deliveryConfig.name)
      }
    )
      .isEmpty()
  }

}
