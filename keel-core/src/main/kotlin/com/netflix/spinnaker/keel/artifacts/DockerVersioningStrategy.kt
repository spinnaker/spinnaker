package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.core.TagComparator

data class DockerVersioningStrategy(
  val strategy: TagVersionStrategy,
  val captureGroupRegex: String? = null
) : VersioningStrategy {
  override val type: String = "docker"

  override val comparator: Comparator<String> =
    TagComparator(strategy, captureGroupRegex)

  override fun toString(): String =
    "${javaClass.simpleName}[strategy=$strategy, captureGroupRegex=$captureGroupRegex]}"
}
