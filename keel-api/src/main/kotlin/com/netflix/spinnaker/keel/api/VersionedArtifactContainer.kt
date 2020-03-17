package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

/**
 * Interface implemented by [ResourceSpec] types that contain versioned artifacts, typically compute resources.
 */
interface VersionedArtifactContainer {
  val deliveryArtifact: DeliveryArtifact?
  val artifactVersion: String?
}

interface ComputeResourceSpec : ResourceSpec, VersionedArtifactContainer
