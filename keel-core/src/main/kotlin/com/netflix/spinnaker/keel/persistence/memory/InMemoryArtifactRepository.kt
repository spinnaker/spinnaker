package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository

class InMemoryArtifactRepository : ArtifactRepository {
  private val artifacts: MutableMap<DeliveryArtifact, MutableList<DeliveryArtifactVersion>> =
    mutableMapOf()

  override fun store(artifact: DeliveryArtifact) {
    artifacts[artifact] = mutableListOf()
  }

  override fun store(artifactVersion: DeliveryArtifactVersion) {
    val versions = artifacts[artifactVersion.artifact] ?: throw  IllegalArgumentException()
    if (versions.none { it.version == artifactVersion.version }) {
      versions.add(0, artifactVersion)
    }
  }

  override fun isRegistered(name: String, type: ArtifactType) =
    artifacts.keys.any {
      it.name == name && it.type == type
    }

  override fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion> =
    artifacts[artifact] ?: emptyList()

  fun dropAll() {
    artifacts.clear()
  }
}
