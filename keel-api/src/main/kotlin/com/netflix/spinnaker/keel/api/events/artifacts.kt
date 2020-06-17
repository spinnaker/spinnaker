package com.netflix.spinnaker.keel.api.events

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.SpinnakerArtifact

/**
 * An event that conveys information about one or more software artifacts that are
 * potentially relevant to keel.
 */
data class ArtifactEvent(
  val artifacts: List<SpinnakerArtifact>,
  val details: Map<String, Any>?
)

/**
 * Event emitted with a new [DeliveryArtifact] is registered.
 */
data class ArtifactRegisteredEvent(
  val artifact: DeliveryArtifact
)

/**
 * Event emitted to trigger synchronization of artifact information.
 */
data class ArtifactSyncEvent(
  val controllerTriggered: Boolean = false
)
