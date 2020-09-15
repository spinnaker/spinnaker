package com.netflix.spinnaker.keel.api.artifacts

import com.netflix.spinnaker.keel.api.schema.Discriminator

/**
 * Strategy for how to sort versions of artifacts.
 */
interface VersioningStrategy {
  @Discriminator
  val type: String
  val comparator: Comparator<String>
}
