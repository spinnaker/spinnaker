package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion

interface ArtifactRepository {

  fun store(artifact: DeliveryArtifact)

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun store(artifactVersion: DeliveryArtifactVersion)

  fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion>

}
