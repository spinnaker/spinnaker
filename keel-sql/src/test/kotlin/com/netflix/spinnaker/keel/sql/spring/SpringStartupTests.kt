package com.netflix.spinnaker.keel.sql.spring

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlDeliveryConfigRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isA

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK,
  properties = [
    "spinnaker.baseUrl=http://spinnaker",
    "sql.enabled=true",
    "sql.connection-pools.default.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "sql.migration.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
  ]
)
internal class SpringStartupTests
@Autowired constructor(
  val artifactRepository: ArtifactRepository,
  val resourceRepository: ResourceRepository,
  val deliveryConfigRepository: DeliveryConfigRepository
) {

  @Test
  fun `uses SqlArtifactRepository`() {
    expectThat(artifactRepository).isA<SqlArtifactRepository>()
  }

  @Test
  fun `uses SqlResourceRepository`() {
    expectThat(resourceRepository).isA<SqlResourceRepository>()
  }

  @Test
  fun `uses SqlDeliveryConfigRepository`() {
    expectThat(deliveryConfigRepository).isA<SqlDeliveryConfigRepository>()
  }
}
