package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * A [SortingStrategy] for NPM packages that compares the packages' semantic versions.
 */
object NpmVersionSortingStrategy : SortingStrategy {
  override val type: String = "npm-versions"

  override val comparator = Comparator<PublishedArtifact> { v1, v2 ->
    NPM_VERSION_COMPARATOR.compare(v1.version, v2.version)
  }

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is NpmVersionSortingStrategy
  }
}
