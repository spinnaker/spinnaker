package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException

class InMemoryArtifactRepository : ArtifactRepository {
  private val artifacts: MutableMap<DeliveryArtifact, MutableList<String>> =
    mutableMapOf()

  override fun register(artifact: DeliveryArtifact) {
    if (artifacts.containsKey(artifact)) {
      throw ArtifactAlreadyRegistered(artifact)
    }
    artifacts[artifact] = mutableListOf()
  }

  override fun store(artifact: DeliveryArtifact, version: String): Boolean {
    if (!artifacts.containsKey(artifact)) {
      throw NoSuchArtifactException(artifact)
    }
    val versions = artifacts[artifact] ?: throw  IllegalArgumentException()
    return if (versions.none { it == version }) {
      versions.add(0, version)
      true
    } else {
      false
    }
  }

  override fun isRegistered(name: String, type: ArtifactType) =
    artifacts.keys.any {
      it.name == name && it.type == type
    }

  override fun versions(artifact: DeliveryArtifact): List<String> =
    artifacts[artifact] ?: throw NoSuchArtifactException(artifact)

  fun dropAll() {
    artifacts.clear()
  }
}
