package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * A [SortingStrategy] for Docker images that compares version tags.
 */
data class DockerVersionSortingStrategy(
  val strategy: TagVersionStrategy,
  val captureGroupRegex: String? = null
) : SortingStrategy {
  override val type: String = "docker-versions"
  private val tagComparator = TagComparator(strategy, captureGroupRegex)

  override val comparator = Comparator<PublishedArtifact> { v1, v2 ->
    tagComparator.compare(v1.version, v2.version)
  }

  override fun toString(): String =
    "${javaClass.simpleName}[strategy=$strategy, captureGroupRegex=$captureGroupRegex]}"
}
