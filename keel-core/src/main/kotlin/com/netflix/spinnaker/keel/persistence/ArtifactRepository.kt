package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion

interface ArtifactRepository {

  fun store(artifact: DeliveryArtifact)
  fun store(artifactVersion: DeliveryArtifactVersion)

  // TODO: should this just return boolean?
  fun get(name: String, type: ArtifactType): DeliveryArtifact?

  fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion>

}
