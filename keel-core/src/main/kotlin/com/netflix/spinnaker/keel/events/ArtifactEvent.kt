package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.kork.artifacts.model.Artifact

data class ArtifactEvent(
  val artifacts: List<Artifact>,
  val details: Map<String, Any>?
)

/**
 * This event optionally takes [statuses] so that we can fetch the most relevant first artifact.
 */
data class ArtifactRegisteredEvent(
  val artifact: DeliveryArtifact,
  val statuses: List<ArtifactStatus> = enumValues<ArtifactStatus>().toList()
)
