package com.netflix.spinnaker.keel.events

/**
 * An event fired when we launch a task to deploy a new version to a cluster
 */
data class ArtifactVersionDeploying(
  val resourceId: String,
  val artifactVersion: String
)
