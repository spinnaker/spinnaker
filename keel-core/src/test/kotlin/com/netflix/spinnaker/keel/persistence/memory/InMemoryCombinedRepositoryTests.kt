package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier

class InMemoryCombinedRepositoryTests : CombinedRepositoryTests<InMemoryDeliveryConfigRepository, InMemoryResourceRepository, InMemoryArtifactRepository>() {
  private val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
  private val resourceRepository = InMemoryResourceRepository()
  private val artifactRepository = InMemoryArtifactRepository()
  private val pausedRepository = InMemoryPausedRepository()

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier) =
    deliveryConfigRepository

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier) =
    resourceRepository

  override fun createArtifactRepository() =
    artifactRepository

  override fun flush() {
    deliveryConfigRepository.dropAll()
    resourceRepository.dropAll()
    artifactRepository.dropAll()
    pausedRepository.flush()
  }
}
