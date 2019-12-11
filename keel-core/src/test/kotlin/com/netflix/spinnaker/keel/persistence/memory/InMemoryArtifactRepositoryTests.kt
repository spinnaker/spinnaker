package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import java.time.Clock

class InMemoryArtifactRepositoryTests : ArtifactRepositoryTests<InMemoryArtifactRepository>() {
  override fun factory(clock: Clock) = InMemoryArtifactRepository()

  override fun InMemoryArtifactRepository.flush() {
    dropAll()
  }

  private val deliveryConfigRepository = InMemoryDeliveryConfigRepository(
    Clock.systemDefaultZone())

  override fun Fixture<InMemoryArtifactRepository>.persist() {
    with(subject) {
      register(artifact1)
      setOf(version1, version2, version3).forEach {
        store(artifact1, it, SNAPSHOT)
      }
      register(artifact2)
      setOf(version1, version2, version3).forEach {
        store(artifact2, it, SNAPSHOT)
      }
    }
    deliveryConfigRepository.store(manifest)
  }
}
