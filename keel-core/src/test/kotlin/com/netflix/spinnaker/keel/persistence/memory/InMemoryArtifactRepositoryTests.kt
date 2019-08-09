package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests

class InMemoryArtifactRepositoryTests : ArtifactRepositoryTests<InMemoryArtifactRepository>() {
  override fun factory() = InMemoryArtifactRepository()

  override fun InMemoryArtifactRepository.flush() {
    dropAll()
  }
}
