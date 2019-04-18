package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests

internal class InMemoryArtifactRepositoryTests : ArtifactRepositoryTests<InMemoryArtifactRepository>() {
  override fun factory() = InMemoryArtifactRepository()
}
