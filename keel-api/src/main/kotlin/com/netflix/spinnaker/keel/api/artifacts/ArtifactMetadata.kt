package com.netflix.spinnaker.keel.api.artifacts

/**
 * A wrapper class which holds a single artifact metadata, split into build and git data.
 */
data class ArtifactMetadata(
  val buildMetadata: BuildMetadata?,
  val gitMetadata: GitMetadata?
)
