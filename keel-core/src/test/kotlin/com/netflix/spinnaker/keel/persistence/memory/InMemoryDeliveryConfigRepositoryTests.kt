package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier

internal class InMemoryDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<InMemoryDeliveryConfigRepository, InMemoryResourceRepository, InMemoryArtifactRepository>() {

  private val artifactRepository = InMemoryArtifactRepository()

  override fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier): InMemoryDeliveryConfigRepository =
    InMemoryDeliveryConfigRepository()

  override fun createResourceRepository(): InMemoryResourceRepository =
    InMemoryResourceRepository()

  override fun createArtifactRepository(): InMemoryArtifactRepository =
    InMemoryArtifactRepository()
}
