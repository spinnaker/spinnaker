package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests

internal class InMemoryDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<InMemoryDeliveryConfigRepository>() {

  val resourceRepository = InMemoryResourceRepository()
  val artifactRepository = InMemoryArtifactRepository()

  override fun factory(resourceTypeIdentifier: (String) -> Class<*>): InMemoryDeliveryConfigRepository =
    InMemoryDeliveryConfigRepository(resourceRepository, artifactRepository)
}
