package com.netflix.spinnaker.keel.api.artifacts

import com.netflix.spinnaker.keel.api.schema.Discriminator

/**
 * Strategy for how to sort versions of artifacts.
 */
interface SortingStrategy {
  @Discriminator
  val type: String
  val comparator: Comparator<PublishedArtifact>
}
