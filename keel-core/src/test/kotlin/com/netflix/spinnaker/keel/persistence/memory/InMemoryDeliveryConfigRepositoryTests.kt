package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier

internal class InMemoryDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<InMemoryDeliveryConfigRepository, InMemoryResourceRepository, InMemoryArtifactRepository>() {

  private val artifactRepository = InMemoryArtifactRepository()

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): InMemoryDeliveryConfigRepository =
    InMemoryDeliveryConfigRepository()

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): InMemoryResourceRepository =
    InMemoryResourceRepository()

  override fun createArtifactRepository(): InMemoryArtifactRepository =
    InMemoryArtifactRepository()
}
