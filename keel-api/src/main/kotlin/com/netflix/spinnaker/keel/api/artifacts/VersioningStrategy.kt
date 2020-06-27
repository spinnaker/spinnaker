package com.netflix.spinnaker.keel.api.artifacts

/**
 * Strategy for how to sort versions of artifacts.
 */
interface VersioningStrategy {
  val type: String
  val comparator: Comparator<String>
}
