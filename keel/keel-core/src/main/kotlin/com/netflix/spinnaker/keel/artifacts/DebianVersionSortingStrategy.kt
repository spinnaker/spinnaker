package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * A [SortingStrategy] for Debian packages that compares package versions.
 */
object DebianVersionSortingStrategy : SortingStrategy {
  override val type: String = "debian-versions"

  override val comparator = Comparator<PublishedArtifact> { v1, v2 ->
    DEBIAN_VERSION_COMPARATOR.compare(v1.version, v2.version)
  }

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is DebianVersionSortingStrategy
  }
}
