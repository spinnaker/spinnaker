package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

interface VersionedArtifact {
  val deliveryArtifact: DeliveryArtifact?
  val artifactVersion: String?
}
