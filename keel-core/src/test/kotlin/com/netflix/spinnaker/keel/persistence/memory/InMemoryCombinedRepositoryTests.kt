package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier

class InMemoryCombinedRepositoryTests : CombinedRepositoryTests<InMemoryDeliveryConfigRepository, InMemoryResourceRepository, InMemoryArtifactRepository>() {
  private val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
  private val resourceRepository = InMemoryResourceRepository()
  private val artifactRepository = InMemoryArtifactRepository()

  override fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier) =
    deliveryConfigRepository

  override fun createResourceRepository() =
    resourceRepository

  override fun createArtifactRepository() =
    artifactRepository

  override fun flush() {
    deliveryConfigRepository.dropAll()
    resourceRepository.dropAll()
    artifactRepository.dropAll()
  }
}
