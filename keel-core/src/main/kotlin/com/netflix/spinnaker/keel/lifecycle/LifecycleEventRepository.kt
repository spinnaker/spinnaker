package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

interface LifecycleEventRepository {

  /**
   * Adds event to list of events
   *
   * @return the uid of the saved event
   */
  fun saveEvent(event: LifecycleEvent): String

  /**
   * Returns all raw events for the artifact version
   */
  fun getEvents(artifact: DeliveryArtifact, artifactVersion: String): List<LifecycleEvent>

  /**
   * Returns the event summaries by type ("steps") for an artifact version
   */
  fun getSteps(artifact: DeliveryArtifact, artifactVersion: String): List<LifecycleStep>
}
