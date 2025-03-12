package com.netflix.spinnaker.keel.sql.spring

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlDeliveryConfigRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.ContextHierarchy
import strikt.api.expectThat
import strikt.assertions.isA

@SpringBootTest(
  webEnvironment = MOCK
)
@ContextHierarchy(
  ContextConfiguration(classes = [KeelApplication::class])
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
