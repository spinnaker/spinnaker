package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier

internal class InMemoryDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<InMemoryDeliveryConfigRepository>() {

  val resourceRepository = InMemoryResourceRepository()
  val artifactRepository = InMemoryArtifactRepository()

  override fun factory(resourceTypeIdentifier: ResourceTypeIdentifier): InMemoryDeliveryConfigRepository =
    InMemoryDeliveryConfigRepository(resourceRepository, artifactRepository)
}
