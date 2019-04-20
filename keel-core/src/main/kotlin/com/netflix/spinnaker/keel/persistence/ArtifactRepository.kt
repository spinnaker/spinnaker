package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion

interface ArtifactRepository {

  fun register(artifact: DeliveryArtifact)

  fun isRegistered(name: String, type: ArtifactType): Boolean

  /**
   * @return `true` if a new version is persisted, `false` if the specified version was already
   * known (in which case this method is a no-op).
   */
  fun store(artifactVersion: DeliveryArtifactVersion): Boolean

  fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion>

}
