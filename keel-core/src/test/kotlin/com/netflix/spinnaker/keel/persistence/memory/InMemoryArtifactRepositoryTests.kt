package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import java.time.Clock

class InMemoryArtifactRepositoryTests : ArtifactRepositoryTests<InMemoryArtifactRepository>() {
  override fun factory(clock: Clock) = InMemoryArtifactRepository()

  override fun InMemoryArtifactRepository.flush() {
    dropAll()
  }

  private val deliveryConfigRepository = InMemoryDeliveryConfigRepository(
    Clock.systemDefaultZone())

  override fun persist(manifest: DeliveryConfig) {
    deliveryConfigRepository.store(manifest)
  }
}
