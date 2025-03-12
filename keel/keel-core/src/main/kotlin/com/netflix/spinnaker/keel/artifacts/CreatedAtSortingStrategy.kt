package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * A [SortingStrategy] that compares artifact versions by creation timestamp.
 */
object CreatedAtSortingStrategy : SortingStrategy {
  override val type: String = "created-at"

  override val comparator: Comparator<PublishedArtifact> =
    compareByDescending { it.createdAt }

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is CreatedAtSortingStrategy
  }
}
