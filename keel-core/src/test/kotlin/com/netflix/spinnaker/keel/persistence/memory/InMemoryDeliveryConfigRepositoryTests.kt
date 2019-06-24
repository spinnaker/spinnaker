package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.persistence.ResourceRepository

internal class InMemoryDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<InMemoryDeliveryConfigRepository>() {
  override fun factory(
    artifactRepository: ArtifactRepository,
    resourceRepository: ResourceRepository
  ) =
    InMemoryDeliveryConfigRepository(artifactRepository, resourceRepository)
}
