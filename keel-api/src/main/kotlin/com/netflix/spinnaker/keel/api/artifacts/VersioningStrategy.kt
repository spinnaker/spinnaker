package com.netflix.spinnaker.keel.api.artifacts

/**
 * Strategy for how to sort versions of artifacts.
 */
abstract class VersioningStrategy {
  abstract val type: String
  abstract val comparator: Comparator<String>
}
