package com.netflix.spinnaker.keel.artifact.events

import com.netflix.spinnaker.kork.artifacts.model.Artifact

data class KorkArtifactEvent(
  val artifacts: List<Artifact>,
  val details: Map<String, Any>?
)
